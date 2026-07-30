package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.LotValuation;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationLineView;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationView;
import gr.novotrade.novocore.core.api.purchasing.InvalidFreightAllocationException;
import gr.novotrade.novocore.core.api.purchasing.NewFreightAllocation;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceLineView;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.ProportionalAllocation;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Freight and duty allocated into the lots they delivered — brief §4, Q18, ADR 0010.
 *
 * <p><strong>In the purchasing slice, not the inventory one.</strong> What an allocation is doing is
 * spending a supplier's invoice: it reads a purchase invoice line and a goods receipt directly, both
 * of which live here, and reaches inventory the way every other consumer does, through
 * {@code InventoryService}. The other direction would be a bean cycle — {@code InventoryServiceImpl}
 * cannot depend on purchasing, which already depends on it.
 *
 * <p><strong>Two roundings, and each is where it has to be.</strong> The split across lots is exact
 * integer cents ({@code ProportionalAllocation}), so the shares sum to the amount allocated and the
 * entry balances by construction. The only genuine rounding is the per-unit increment, which is six
 * decimals with the mode from {@code ledger.rounding.mode}.
 *
 * <p><strong>The split of one lot's share changed in step 13 (ADR 0015), and the change is the
 * point.</strong> The capitalised half used to be a second proportional split — the share divided by
 * what was still in the lot against what had gone. It is now <em>exactly how much this allocation
 * raises that lot's carrying value</em>, which is the figure that will actually reach the Inventory
 * control account. The old way put a proportional estimate on the Inventory line and left the lot
 * carrying something else, which is Q45 reached from this direction.
 *
 * <p>ADR 0010 is untouched: the two halves are still "what the stock on hand carries" and "what
 * belongs to stock already gone". What changed is that the first is now stated exactly and the
 * second is the remainder — and the remainder can be one cent <em>negative</em>, because a
 * six-decimal per-unit cost cannot always express a total. {@code V24} relaxed the CHECK that
 * forbade that and explains it at length; {@code Landed cost variance} is credited in that case,
 * which needs nothing new, since ADR 0011's return catch-up already credits it.
 */
@Service
class FreightAllocationServiceImpl implements FreightAllocationService {

    private static final String ENTITY_TYPE = "FreightAllocation";

    private final FreightAllocationRepository allocations;
    private final FreightAllocationLineRepository allocationLines;
    private final PurchaseInvoiceLineRepository invoiceLines;
    private final PurchaseInvoiceRepository invoices;
    private final GoodsReceiptLineRepository receiptLines;
    private final GoodsReceiptRepository receipts;
    private final ChartOfAccountsService chartOfAccounts;
    private final InventoryService inventory;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    FreightAllocationServiceImpl(FreightAllocationRepository allocations,
            FreightAllocationLineRepository allocationLines,
            PurchaseInvoiceLineRepository invoiceLines, PurchaseInvoiceRepository invoices,
            GoodsReceiptLineRepository receiptLines, GoodsReceiptRepository receipts,
            ChartOfAccountsService chartOfAccounts, InventoryService inventory,
            JournalService journal, SettingsService settings, AuditLogService auditLog) {
        this.allocations = allocations;
        this.allocationLines = allocationLines;
        this.invoiceLines = invoiceLines;
        this.invoices = invoices;
        this.receiptLines = receiptLines;
        this.receipts = receipts;
        this.chartOfAccounts = chartOfAccounts;
        this.inventory = inventory;
        this.journal = journal;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Allocating
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public FreightAllocationView allocate(NewFreightAllocation request) {
        Objects.requireNonNull(request, "request");

        PurchaseInvoiceLine sourceLine = requireAllocatableSource(request);
        Currency currency = request.amount().currency();
        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);

        List<InventoryLotView> lots = new ArrayList<>(request.lotIds().size());
        List<BigDecimal> weights = new ArrayList<>(request.lotIds().size());
        for (long lotId : request.lotIds()) {
            InventoryLotView lot = requireAllocatableLot(lotId, currency);
            lots.add(lot);
            weights.add(lot.landedCostBasis());
        }

        // Exact integer cents, largest remainder: the shares sum to the amount allocated, so the
        // credit to Freight / Landed Cost — Unallocated is matched to the cent by what was debited.
        List<Money> shares = ProportionalAllocation.proportionally(request.amount(), weights);

        List<AllocatedLot> allocated = new ArrayList<>(lots.size());
        for (int i = 0; i < lots.size(); i++) {
            allocated.add(split(lots.get(i), shares.get(i), currency, roundingMode));
        }

        long entryId = post(request, sourceLine, allocated, currency).id();

        FreightAllocation allocation = new FreightAllocation(sourceLine, request.allocationDate(),
                request.description(), null);
        allocation.postedAs(entryId);
        for (AllocatedLot lot : allocated) {
            allocation.addLine(new FreightAllocationLine(lot.lot().id(),
                    lot.lot().quantityRemaining(), lot.capitalised(), lot.variance(),
                    lot.perUnit()));
        }
        FreightAllocation saved = allocations.save(allocation);

        // The lots move only after the posting has been accepted — the write-off's order, and for its
        // reason: the transaction guarantees both or neither, and doing it this way round means a
        // rejected entry never leaves a half-applied cost behind in the entity graph either.
        for (AllocatedLot lot : allocated) {
            if (lot.movesTheLot()) {
                inventory.applyLandedCost(lot.lot().id(), lot.perUnit());
            }
        }

        auditLog.record("freight-allocation.posted", ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of(
                        "sourceInvoiceLine", String.valueOf(sourceLine.getId()),
                        "supplierInvoiceNumber", sourceLine.getInvoice().getSupplierInvoiceNumber(),
                        "amount", request.amount().toString(),
                        "capitalised", totalOf(allocated, AllocatedLot::capitalised, currency)
                                .toString(),
                        "variance", totalOf(allocated, AllocatedLot::variance, currency).toString(),
                        "lots", String.valueOf(allocated.size()),
                        "allocationDate", request.allocationDate().toString(),
                        "journalEntry", String.valueOf(entryId)));

        return toView(saved);
    }

    @Override
    @Transactional
    public FreightAllocationView reverse(long allocationId, LocalDate reversalDate, String reason) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        FreightAllocation original = load(allocationId);

        if (original.isReversal()) {
            throw new InvalidFreightAllocationException(
                    "Freight allocation " + allocationId + " is itself the reversal of allocation "
                            + original.getReversalOfId() + ". Reversing it would put the cost back "
                            + "onto the lots while claiming to be a correction; allocate it again "
                            + "instead, so the ledger says what actually happened.");
        }
        allocations.findByReversalOfId(allocationId).ifPresent(existing -> {
            throw new InvalidFreightAllocationException(
                    "Freight allocation " + allocationId + " has already been reversed by allocation "
                            + existing.getId() + ". Reversing it twice would take the cost off the "
                            + "lots twice and credit Inventory twice, with both halves looking "
                            + "correct.");
        });

        // ADR 0010's refusal, and ADR 0008's principle behind it: stock that left after this posted
        // was costed out at the raised figure, so the freight is already inside a posted cost of goods
        // sold. Crediting Inventory back for it would credit stock that is not there. Refused rather
        // than partly undone — the correction is a new allocation onto the lots it belonged to.
        for (FreightAllocationLine line : original.getLines()) {
            InventoryLotView lot = inventory.requireLot(line.getLotId());
            if (lot.quantityRemaining().compareTo(line.getQuantityRemainingAtAllocation()) != 0) {
                throw new InvalidFreightAllocationException(
                        "Lot " + lot.id() + " had " + line.getQuantityRemainingAtAllocation()
                                + " left when freight allocation " + allocationId + " posted and has "
                                + lot.quantityRemaining() + " now, so this allocation cannot be "
                                + "un-made: what left in between was costed out at the unit cost this "
                                + "allocation raised, and that cost of goods sold has posted. Allocate "
                                + "the freight again onto the lots it really belonged to instead.");
            }
        }

        long reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                original.getJournalEntryId(),
                reversalDate,
                "Reversal of freight allocation " + allocationId + " ("
                        + original.getSourceLine().getInvoice().getSupplierInvoiceNumber() + ")"
                        + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()),
                JournalSource.FREIGHT_ALLOCATION,
                journal.mirrorOf(original.getJournalEntryId()))).id();

        for (FreightAllocationLine line : original.getLines()) {
            if (!line.getLandedUnitCost().isZero()) {
                inventory.removeLandedCost(line.getLotId(), line.getLandedUnitCost());
            }
        }

        // No lines of its own: the original's are what a reader needs and what this reversal just
        // read to undo itself. PurchaseInvoice's stance.
        FreightAllocation reversal = new FreightAllocation(original.getSourceLine(), reversalDate,
                reason == null || reason.isBlank() ? null : reason.trim(), allocationId);
        reversal.postedAs(reversingEntryId);
        FreightAllocation saved = allocations.save(reversal);

        auditLog.record("freight-allocation.reversed", ENTITY_TYPE, String.valueOf(allocationId),
                Map.of(
                        "reversedBy", String.valueOf(saved.getId()),
                        "lots", String.valueOf(original.getLines().size()),
                        "reversalDate", reversalDate.toString(),
                        "journalEntry", String.valueOf(reversingEntryId)));

        return toView(saved);
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<FreightAllocationView> find(long allocationId) {
        return allocations.findById(allocationId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public FreightAllocationView require(long allocationId) {
        return toView(load(allocationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreightAllocationView> ofPurchaseInvoiceLine(long purchaseInvoiceLineId) {
        return allocations.findBySourceLineIdOrderByIdAsc(purchaseInvoiceLineId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreightAllocationView> ofLot(long lotId) {
        return allocations.findTouchingLot(lotId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreightAllocationView> between(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no allocations in it.");
        }
        return allocations.findByAllocationDateBetweenOrderByAllocationDateAscIdAsc(from, to)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money unallocatedAmountOf(long purchaseInvoiceLineId) {
        PurchaseInvoiceLine line = invoiceLines.findById(purchaseInvoiceLineId)
                .orElseThrow(() -> PurchaseInvoiceNotFoundException.forFreightSourceLine(
                        purchaseInvoiceLineId));
        if (!isFreightLine(line)) {
            // Zero rather than a refusal: "how much of the electricity bill is waiting to be
            // allocated into stock" has an honest answer, and it is none.
            return Money.zero(line.getNetAmount().currency());
        }
        return unallocatedOf(line);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceLineView> linesAwaitingAllocation() {
        AccountView freight =
                chartOfAccounts.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED);
        return invoiceLines.findAwaitingAllocation(freight.id()).stream()
                .map(PurchaseInvoiceLineViews::expense)
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Validation — what is refused rather than resolved
    // ---------------------------------------------------------------------------------------

    private PurchaseInvoiceLine requireAllocatableSource(NewFreightAllocation request) {
        PurchaseInvoiceLine line = invoiceLines.findById(request.purchaseInvoiceLineId())
                .orElseThrow(() -> PurchaseInvoiceNotFoundException.forFreightSourceLine(
                        request.purchaseInvoiceLineId()));

        if (line.isInventory()) {
            throw new InvalidFreightAllocationException(
                    "Purchase invoice line " + line.getId() + " is an inventory line, so it is already "
                            + "the goods rather than a cost of getting them here. Allocating it into "
                            + "lots would capitalise the same purchase twice.");
        }
        AccountView freight =
                chartOfAccounts.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED);
        if (!Objects.equals(line.getExpenseAccountId(), freight.id())) {
            AccountView stated = chartOfAccounts.requireAccount(line.getExpenseAccountId());
            throw new InvalidFreightAllocationException(
                    "Purchase invoice line " + line.getId() + " was posted to '" + stated.name()
                            + "', not to '" + freight.name() + "'. An allocation credits the "
                            + "unallocated landed cost account, so allocating a cost that was never "
                            + "debited there would drive it negative while the real expense sat "
                            + "untouched where it was booked.");
        }

        PurchaseInvoice invoice = line.getInvoice();
        if (invoice.isReversal()) {
            throw new InvalidFreightAllocationException(
                    "Purchase invoice line " + line.getId() + " belongs to a reversing document, which "
                            + "un-made a cost rather than incurring one.");
        }
        invoices.findByReversalOfId(invoice.getId()).ifPresent(reversal -> {
            throw new InvalidFreightAllocationException(
                    "Purchase invoice " + invoice.getId() + " was reversed by document "
                            + reversal.getId() + ", so there is no longer a freight cost on it to "
                            + "allocate. Record the corrected invoice and allocate from that.");
        });

        Money net = line.getNetAmount();
        if (!net.currency().equals(request.amount().currency())) {
            throw new InvalidFreightAllocationException(
                    "This freight line is in " + net.currency().getCurrencyCode()
                            + " and the allocation is in "
                            + request.amount().currency().getCurrencyCode()
                            + ". NovoCore does not convert between currencies and will not silently "
                            + "pick one (ADR 0005).");
        }

        Money unallocated = unallocatedOf(line);
        if (request.amount().compareTo(unallocated) > 0) {
            throw new InvalidFreightAllocationException(
                    "Purchase invoice line " + line.getId() + " charged " + net + ", of which "
                            + net.minus(unallocated) + " is already allocated, so " + request.amount()
                            + " cannot be. Allocating more than was incurred would credit Freight / "
                            + "Landed Cost — Unallocated below zero and capitalise a cost nobody paid.");
        }
        return line;
    }

    private InventoryLotView requireAllocatableLot(long lotId, Currency currency) {
        InventoryLotView lot = inventory.requireLot(lotId);

        if (!lot.receivedUnitCost().currency().equals(currency)) {
            throw new InvalidFreightAllocationException(
                    "Lot " + lotId + " was received in "
                            + lot.receivedUnitCost().currency().getCurrencyCode()
                            + " and this freight is in " + currency.getCurrencyCode()
                            + ". NovoCore does not convert (ADR 0005), so there is no proportion "
                            + "between the two.");
        }
        if (lot.landedCostBasis().signum() == 0) {
            // Proportional-by-value gives a zero-value lot no share, so including it would be naming
            // stock the allocation is not going to cost. Refused rather than silently allocated
            // nothing, which is the same distinction the whole design keeps drawing.
            throw new InvalidFreightAllocationException(
                    "Lot " + lotId + " ('" + lot.productSku() + "') was received at a cost of zero, so "
                            + "proportional-by-value allocation (brief §4) gives it no share of this "
                            + "freight. Naming it here would claim to have costed stock that got "
                            + "nothing; leave it out, or allocate by a rule this system does not have.");
        }

        lot.sourceReceiptLine().ifPresent(receiptLineId -> {
            GoodsReceiptLine receiptLine = receiptLines.findById(receiptLineId).orElseThrow(() ->
                    new IllegalStateException(
                            "Lot " + lotId + " names goods receipt line " + receiptLineId
                                    + ", which does not exist. A foreign key makes that impossible."));
            GoodsReceipt receipt = receiptLine.getReceipt();
            boolean unmade = receipt.isReversal()
                    || receipts.findByReversalOfId(receipt.getId()).isPresent();
            if (unmade) {
                throw new InvalidFreightAllocationException(
                        "Lot " + lotId + " came from goods receipt " + receipt.getId() + ", which has "
                                + "been reversed — that delivery was un-made, so nothing was carried "
                                + "to it. Allocate onto the lots of the delivery that was actually "
                                + "received.");
            }
        });
        return lot;
    }

    private boolean isFreightLine(PurchaseInvoiceLine line) {
        if (line.isInventory()) {
            return false;
        }
        AccountView freight =
                chartOfAccounts.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED);
        return Objects.equals(line.getExpenseAccountId(), freight.id());
    }

    private Money unallocatedOf(PurchaseInvoiceLine line) {
        Money net = line.getNetAmount();
        Money already = Money.of(
                allocationLines.allocatedAgainstLine(line.getId()), net.currency());
        return net.minus(already);
    }

    // ---------------------------------------------------------------------------------------
    // Q18's arithmetic — one lot's share, split by what is still in the lot
    // ---------------------------------------------------------------------------------------

    /**
     * Divides one lot's share into the half that raises its carrying cost and the half that cannot.
     *
     * <p>The per-unit increment comes first, because it decides whether the lot can move at all: a
     * share so small that it rounds to nothing per unit belongs entirely in variance, rather than
     * debiting Inventory for a cost no lot ends up carrying.
     *
     * <p>Otherwise the split is {@code ProportionalAllocation} again, weighted by what is left against
     * what has gone. Exact and non-negative in both halves, which matters: a residual computed as
     * {@code share − capitalised} could come out negative for a very large quantity, and the account
     * it posts to only ever takes a debit.
     */
    private static AllocatedLot split(InventoryLotView lot, Money share, Currency currency,
            RoundingMode roundingMode) {
        Quantity received = lot.quantityReceived();
        Quantity remaining = lot.quantityRemaining();

        UnitCost perUnit = new UnitCost(
                share.amount().divide(received.value(), UnitCost.SCALE, roundingMode), currency);

        if (perUnit.isZero() || !remaining.isPositive()) {
            // Nothing this allocation can do to the lot: either the share does not reach a unit, or
            // there are no units left to carry it. Q18's case, in both of its shapes.
            return new AllocatedLot(lot, Money.zero(currency), share, UnitCost.zero(currency));
        }

        // ADR 0015. The capitalised half is not a proportional estimate of what the stock on hand
        // should absorb — it is *exactly* how much this allocation raises the lot's carrying value,
        // which is the amount that will land on the Inventory control account when perUnit is
        // applied. Computing it any other way puts a figure on the Inventory line that the lot does
        // not agree with, which is the defect Q45 recorded, reached from the other direction.
        //
        // ADR 0010 is untouched by this: the split is still "the part the stock on hand carries" and
        // "the part belonging to stock already gone". This says the first half exactly rather than
        // approximately, and the second is what is left over — which also absorbs the fraction of a
        // cent that expressing a total as a six-decimal per-unit cost cannot represent.
        Money capitalised =
                LotValuation.valueRecosted(lot.unitCost(), lot.unitCost().plus(perUnit), remaining);
        return new AllocatedLot(lot, capitalised, share.minus(capitalised), perUnit);
    }

    /**
     * One lot's outcome, carried from the arithmetic through to the posting and the stored line.
     *
     * <p>Computed once, for the reason {@code PurchaseInvoiceServiceImpl.CostedLine} exists: the
     * second computation is where the two would drift apart.
     */
    private record AllocatedLot(
            InventoryLotView lot, Money capitalised, Money variance, UnitCost perUnit) {

        /** True when this lot's unit cost actually changes — which is exactly when it capitalises. */
        boolean movesTheLot() {
            return !perUnit.isZero() && capitalised.isPositive();
        }
    }

    private static Money totalOf(List<AllocatedLot> lots,
            java.util.function.Function<AllocatedLot, Money> part, Currency currency) {
        Money total = Money.zero(currency);
        for (AllocatedLot lot : lots) {
            total = total.plus(part.apply(lot));
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    /**
     * One entry: a debit to {@code Inventory} per lot that capitalised, a debit to
     * {@code Landed cost variance} per lot whose stock had already gone, and one credit to
     * {@code Freight / Landed Cost — Unallocated} for the whole amount.
     *
     * <p>Both debit sides carry the lot's {@code SubLedgerRef} — the Inventory one because it is a
     * Control account and must, the variance one because knowing which lot a cost came out of is what
     * having lots is for, which is the same allowance {@code Cost of goods sold} uses.
     *
     * <p>It balances by construction rather than by arithmetic that has to be got right twice: the
     * shares sum to the amount by {@code ProportionalAllocation}, and each share is its own two halves.
     */
    private gr.novotrade.novocore.core.api.ledger.JournalEntryView post(NewFreightAllocation request,
            PurchaseInvoiceLine sourceLine, List<AllocatedLot> allocated, Currency currency) {
        AccountView inventoryAccount = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY);
        AccountView varianceAccount =
                chartOfAccounts.requireAccount(AccountSystemKey.LANDED_COST_VARIANCE);
        AccountView freight =
                chartOfAccounts.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED);

        List<NewJournalLine> lines = new ArrayList<>();
        for (AllocatedLot lot : allocated) {
            SubLedgerRef lotRef = SubLedgerRef.inventoryLot(lot.lot().id());
            if (lot.capitalised().isPositive()) {
                lines.add(NewJournalLine.debit(inventoryAccount.id(), lot.capitalised())
                        .forSubLedger(lotRef)
                        .describedAs("Landed cost — " + lot.lot().productSku()
                                + " (lot " + lot.lot().id() + ")"));
            }
            if (lot.variance().isPositive()) {
                lines.add(NewJournalLine.debit(varianceAccount.id(), lot.variance())
                        .forSubLedger(lotRef)
                        .describedAs("Landed cost on stock already sold — "
                                + lot.lot().productSku() + " (lot " + lot.lot().id() + ")"));
            } else if (lot.variance().isNegative()) {
                // The cent a six-decimal per-unit cost cannot express, arriving from the other
                // direction — see V24. The lot's carrying value rose by one more than its share, so
                // the difference is credited back rather than debited out.
                lines.add(NewJournalLine.credit(varianceAccount.id(), lot.variance().abs())
                        .forSubLedger(lotRef)
                        .describedAs("Landed cost not expressible per unit — "
                                + lot.lot().productSku() + " (lot " + lot.lot().id() + ")"));
            }
        }
        lines.add(NewJournalLine.credit(freight.id(), request.amount()));

        String description = request.description() != null
                ? request.description()
                : "Freight allocation — " + sourceLine.getInvoice().getSupplierInvoiceNumber()
                        + " across " + allocated.size() + " lot"
                        + (allocated.size() == 1 ? "" : "s");

        return journal.post(NewJournalEntry.of(request.allocationDate(), description,
                JournalSource.FREIGHT_ALLOCATION, lines));
    }

    // ---------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------

    private FreightAllocation load(long allocationId) {
        return allocations.findById(allocationId)
                .orElseThrow(() -> new FreightAllocationNotFoundException(allocationId));
    }

    private FreightAllocationView toView(FreightAllocation allocation) {
        PurchaseInvoiceLine sourceLine = allocation.getSourceLine();
        Currency currency = sourceLine.getNetAmount().currency();

        // One read per lot. A document names the lots one shipment covered, so this is a handful of
        // rows rather than a list — the batched form belongs with the first caller that needs it.
        Map<Long, InventoryLotView> lots = new LinkedHashMap<>();
        for (FreightAllocationLine line : allocation.getLines()) {
            lots.computeIfAbsent(line.getLotId(), inventory::requireLot);
        }

        // A reversal keeps no lines of its own — the original's are what a reader needs, and this
        // reversal read exactly those to undo itself. But its *totals* must still say what it did.
        //
        // Found by step 15, and it is the shape this step keeps finding: the ledger was right and
        // the wire was not. Without this, a reversal reports 0.00 for the amount it took back out,
        // so GET /api/freight-allocations?from=&to= overstates freight variance for the period by
        // every reversal in it — a plausible number, wrong, with no document to point at. The
        // reversing journal entry is the exact mirror of the original, so these figures are too.
        if (allocation.isReversal()) {
            FreightAllocationView original = toView(load(allocation.getReversalOfId()));
            return new FreightAllocationView(
                    allocation.getId(),
                    sourceLine.getId(),
                    sourceLine.getInvoice().getId(),
                    sourceLine.getInvoice().getSupplierInvoiceNumber(),
                    allocation.getAllocationDate(),
                    allocation.getDescription(),
                    original.amount().negated(),
                    original.capitalised().negated(),
                    original.variance().negated(),
                    allocation.getJournalEntryId(),
                    allocation.getReversalOfId(),
                    null,
                    List.of());
        }

        List<FreightAllocationLineView> lineViews = new ArrayList<>();
        Money capitalised = Money.zero(currency);
        Money variance = Money.zero(currency);
        for (FreightAllocationLine line : allocation.getLines()) {
            InventoryLotView lot = lots.get(line.getLotId());
            capitalised = capitalised.plus(line.getCapitalisedAmount());
            variance = variance.plus(line.getVarianceAmount());
            lineViews.add(new FreightAllocationLineView(
                    line.getId(),
                    line.getLineNumber(),
                    line.getLotId(),
                    lot.productId(),
                    lot.productSku(),
                    lot.quantityReceived(),
                    line.getQuantityRemainingAtAllocation(),
                    lot.receivedUnitCost(),
                    line.getCapitalisedAmount(),
                    line.getVarianceAmount(),
                    line.getLandedUnitCost()));
        }

        return new FreightAllocationView(
                allocation.getId(),
                sourceLine.getId(),
                sourceLine.getInvoice().getId(),
                sourceLine.getInvoice().getSupplierInvoiceNumber(),
                allocation.getAllocationDate(),
                allocation.getDescription(),
                capitalised.plus(variance),
                capitalised,
                variance,
                allocation.getJournalEntryId(),
                allocation.getReversalOfId(),
                allocations.findByReversalOfId(allocation.getId())
                        .map(FreightAllocation::getId).orElse(null),
                lineViews);
    }
}
