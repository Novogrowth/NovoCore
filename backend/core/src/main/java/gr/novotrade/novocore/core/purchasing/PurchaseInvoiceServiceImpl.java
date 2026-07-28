package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.ledger.VatDimension;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.GrIrMatchView;
import gr.novotrade.novocore.core.api.purchasing.InvalidPurchaseInvoiceException;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceLineView;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptMatch;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
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
 * Recording supplier invoices, and the GR/IR position they leave behind (ADR 0004, ADR 0008).
 *
 * <p><strong>What one invoice posts, all in one entry.</strong> Every inventory line debits GR/IR
 * clearing rather than Inventory, because the Goods Receipt is the inventory event. A matched quantity
 * clears GR/IR at the <em>receipt's</em> unit cost, so the two halves cancel exactly, and the
 * difference against the invoice price becomes the line's variance — which is computed as the
 * residual, so an invoice line's debits always sum to precisely what the supplier charged.
 *
 * <p>The variance goes to its own account and the lot is never touched (ADR 0008): re-costing a lot
 * that FIFO has already consumed into posted COGS is the same problem as editing a posted entry.
 */
@Service
class PurchaseInvoiceServiceImpl implements PurchaseInvoiceService {

    private static final String ENTITY_TYPE = "PurchaseInvoice";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PurchaseInvoiceRepository invoices;
    private final PurchaseInvoiceLineRepository invoiceLines;
    private final GoodsReceiptLineRepository receiptLines;
    private final GrIrMatchRepository matches;
    private final SupplierService suppliers;
    private final ProductService productService;
    private final ChartOfAccountsService chartOfAccounts;
    private final VatClassService vatClasses;
    private final VatExemptionReasonService exemptionReasons;
    private final InventoryService inventory;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    PurchaseInvoiceServiceImpl(PurchaseInvoiceRepository invoices,
            PurchaseInvoiceLineRepository invoiceLines, GoodsReceiptLineRepository receiptLines,
            GrIrMatchRepository matches, SupplierService suppliers, ProductService productService,
            ChartOfAccountsService chartOfAccounts, VatClassService vatClasses,
            VatExemptionReasonService exemptionReasons, InventoryService inventory,
            JournalService journal, SettingsService settings, AuditLogService auditLog) {
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.receiptLines = receiptLines;
        this.matches = matches;
        this.suppliers = suppliers;
        this.productService = productService;
        this.chartOfAccounts = chartOfAccounts;
        this.vatClasses = vatClasses;
        this.exemptionReasons = exemptionReasons;
        this.inventory = inventory;
        this.journal = journal;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public PurchaseInvoiceView record(NewPurchaseInvoice request) {
        Objects.requireNonNull(request, "request");
        SupplierView supplier = suppliers.require(request.supplierId());
        if (!supplier.active()) {
            throw new InvalidPurchaseInvoiceException(
                    "Supplier '" + supplier.name() + "' is inactive, so nothing new should be arriving "
                            + "from them. A supplier is deactivated precisely so that stops.");
        }
        if (invoices.existsStandingInvoice(supplier.id(), request.supplierInvoiceNumber())) {
            throw new InvalidPurchaseInvoiceException(
                    "Supplier '" + supplier.name() + "' has already sent invoice '"
                            + request.supplierInvoiceNumber() + "'. A supplier does not issue the same "
                            + "number twice, so this is a duplicate — which the myDATA import makes a "
                            + "routine hazard rather than a theoretical one, since the same document "
                            + "can arrive from AADE and from a person on the same morning.");
        }

        Currency currency = request.lines().getFirst().currency();
        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);

        List<CostedLine> costed = new ArrayList<>();
        for (NewPurchaseInvoiceLine line : request.lines()) {
            if (!line.currency().equals(currency)) {
                throw new InvalidPurchaseInvoiceException(
                        "This invoice mixes " + currency.getCurrencyCode() + " and "
                                + line.currency().getCurrencyCode() + ". NovoCore does not convert "
                                + "between currencies and will not silently pick one (ADR 0005), so "
                                + "an invoice spanning two has no total to post.");
            }
            costed.add(cost(line, supplier, currency, roundingMode));
        }

        Money gross = costed.stream()
                .map(CostedLine::gross)
                .reduce(Money.zero(currency), Money::plus);
        if (!gross.isPositive()) {
            throw new InvalidPurchaseInvoiceException(
                    "This invoice comes to " + gross + ", so it creates no payable. A supplier who "
                            + "invoices nothing has not sent an invoice — if goods arrived free, the "
                            + "Goods Receipt records that on its own and there is nothing to owe.");
        }

        long entryId = post(request, supplier, costed, currency).id();

        PurchaseInvoice invoice = new PurchaseInvoice(supplier.id(),
                request.supplierInvoiceNumber(), request.invoiceDate(), request.description(), null);
        invoice.postedAs(entryId);
        for (CostedLine line : costed) {
            invoice.addLine(line.toEntity());
        }
        PurchaseInvoice saved = invoices.save(invoice);
        // Flushed so the lines have ids: a match points at one, and a match created before its line
        // exists is the same problem as a lot created before its receipt line.
        invoices.flush();

        List<PurchaseInvoiceLine> savedLines = saved.getLines();
        for (int i = 0; i < costed.size(); i++) {
            CostedLine line = costed.get(i);
            PurchaseInvoiceLine entity = savedLines.get(i);
            for (MatchedQuantity match : line.matches()) {
                matches.save(new GrIrMatch(entity, match.receiptLine(), match.quantity()));
            }
        }

        auditLog.record("purchase-invoice.recorded", ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of(
                        "supplier", supplier.name(),
                        "number", saved.getSupplierInvoiceNumber(),
                        "invoiceDate", saved.getInvoiceDate().toString(),
                        "gross", gross.toString(),
                        "lines", String.valueOf(costed.size()),
                        "journalEntry", String.valueOf(entryId)));

        return toView(saved);
    }

    @Override
    @Transactional
    public PurchaseInvoiceView reverse(long invoiceId, LocalDate reversalDate, String reason) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        PurchaseInvoice original = load(invoiceId);

        if (original.isReversal()) {
            throw new InvalidPurchaseInvoiceException(
                    "Purchase invoice " + invoiceId + " is itself the reversal of invoice "
                            + original.getReversalOfId() + ". Reversing it would re-create the payable "
                            + "while claiming to be a correction; record the invoice again instead.");
        }
        invoices.findByReversalOfId(invoiceId).ifPresent(existing -> {
            throw new InvalidPurchaseInvoiceException(
                    "Purchase invoice " + invoiceId + " has already been reversed by document "
                            + existing.getId() + ". Reversing it twice would credit the supplier twice, "
                            + "with both halves looking correct.");
        });

        // ADR 0008, the other way round: if a lot this invoice priced has since been consumed or
        // written off, its variance is already inside posted COGS, and a reversal claiming otherwise
        // would be the retroactive re-costing the variance account exists to avoid.
        for (GrIrMatch match : matches.findByInvoiceLineInvoiceIdOrderByIdAsc(invoiceId)) {
            long receiptLineId = match.getReceiptLine().getId();
            Optional<InventoryLotView> lot = inventory.findLotByReceiptLine(receiptLineId);
            if (lot.isPresent() && lot.get().quantityConsumed().isPositive()) {
                throw new InvalidPurchaseInvoiceException(
                        "Purchase invoice " + invoiceId + " priced lot " + lot.get().id()
                                + ", and " + lot.get().quantityConsumed() + " of it has since left "
                                + "stock. Its cost — including any purchase price variance this "
                                + "invoice posted — is already inside cost of goods sold, and "
                                + "reversing the invoice would not take it back out. The correction "
                                + "is a supplier credit note against what was actually charged.");
            }
        }

        long reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                original.getJournalEntryId(),
                reversalDate,
                "Reversal of purchase invoice " + invoiceId + " ("
                        + original.getSupplierInvoiceNumber() + ")"
                        + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()),
                JournalSource.PURCHASE_INVOICE,
                journal.mirrorOf(original.getJournalEntryId()))).id();

        // No lines of its own: everything a reader needs is on the original, which stays exactly as
        // posted, and duplicating them would give one purchase two statements of itself.
        PurchaseInvoice reversal = new PurchaseInvoice(original.getSupplierId(),
                original.getSupplierInvoiceNumber(), reversalDate,
                reason == null || reason.isBlank() ? null : reason.trim(), invoiceId);
        reversal.postedAs(reversingEntryId);
        PurchaseInvoice saved = invoices.save(reversal);

        auditLog.record("purchase-invoice.reversed", ENTITY_TYPE, String.valueOf(invoiceId), Map.of(
                "reversedBy", String.valueOf(saved.getId()),
                "reversalDate", reversalDate.toString(),
                "journalEntry", String.valueOf(reversingEntryId)));

        return toView(saved);
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseInvoiceView> find(long invoiceId) {
        return invoices.findById(invoiceId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseInvoiceView require(long invoiceId) {
        return toView(load(invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceView> ofSupplier(long supplierId) {
        return invoices.findBySupplierIdOrderByInvoiceDateAscIdAsc(supplierId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceView> between(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        return invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(from, to).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseInvoiceView> findByJournalEntry(long journalEntryId) {
        return invoices.findByJournalEntryId(journalEntryId).map(this::toView);
    }

    // ---------------------------------------------------------------------------------------
    // The GR/IR position
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceLineView> linesAwaitingDelivery() {
        return invoiceLines.findAwaitingDelivery().stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrIrMatchView> matchesOf(long invoiceId) {
        return matches.findByInvoiceLineInvoiceIdOrderByIdAsc(invoiceId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceView> variancesBetween(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        return invoices.findWithVarianceBetween(from, to).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money totalVarianceBetween(LocalDate from, LocalDate to) {
        List<PurchaseInvoiceView> withVariance = variancesBetween(from, to);
        Money total = Money.zero(Money.EUR);
        for (PurchaseInvoiceView invoice : withVariance) {
            total = total.plus(invoice.variance());
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------
    // Costing one line — where ADR 0008 actually happens
    // ---------------------------------------------------------------------------------------

    private CostedLine cost(NewPurchaseInvoiceLine line, SupplierView supplier, Currency currency,
            RoundingMode roundingMode) {
        ProductView product = null;
        AccountView expenseAccount = null;

        if (line.isInventory()) {
            product = productService.require(line.productId());
            if (!product.active()) {
                throw new InvalidPurchaseInvoiceException(
                        "Product '" + product.sku() + "' is inactive, so nothing new should be bought "
                                + "against it.");
            }
            if (!product.isStocked()) {
                throw new InvalidPurchaseInvoiceException(
                        "Product '" + product.sku() + "' has no stock — it is a "
                                + (product.isBundle() ? "bundle" : product.type().toString())
                                + " — so it cannot pass through GR/IR clearing. A bundle's components "
                                + "are what get bought; a service is an expense line.");
            }
        } else {
            expenseAccount = chartOfAccounts.requireAccount(line.expenseAccountId());
            if (!expenseAccount.active()) {
                throw new InvalidPurchaseInvoiceException(
                        "Account '" + expenseAccount.name() + "' is inactive, so nothing new may post "
                                + "to it.");
            }
            if (expenseAccount.kind() == AccountKind.CONTROL) {
                throw new InvalidPurchaseInvoiceException(
                        "Account '" + expenseAccount.name() + "' is a control account for the "
                                + expenseAccount.subLedgerType() + " sub-ledger, so its balance is "
                                + "that sub-ledger and nothing else. A free-hand invoice line into it "
                                + "is how a control account stops reconciling — which is the one "
                                + "thing control accounts exist to make impossible.");
            }
        }

        VatClassView vatClass = null;
        if (line.vatClassId() != null) {
            vatClass = vatClasses.require(line.vatClassId());
            if (!vatClass.active()) {
                throw new InvalidPurchaseInvoiceException(
                        "VAT class '" + vatClass.code() + "' is inactive. A retired class describes "
                                + "invoices already issued under it, so a new one cannot use it.");
            }
        } else {
            exemptionReasons.require(line.vatExemptionReasonId());
        }
        requireReverseChargeAgreesWithSupplier(line, supplier);

        Money net = line.isInventory()
                ? Money.rounded(line.netExactly(), currency, roundingMode)
                : line.amount();
        Money vat = vatClass == null
                ? Money.zero(currency)
                : Money.rounded(
                        net.amount().multiply(vatClass.ratePercent()).divide(ONE_HUNDRED),
                        currency, roundingMode);

        if (!line.isInventory()) {
            return CostedLine.expense(line, expenseAccount, net, vat);
        }

        // The matched half: each match clears GR/IR at what the DELIVERY put into stock, so the two
        // sides cancel and the invoice's own price never touches the lot.
        List<MatchedQuantity> matched = new ArrayList<>();
        Quantity matchedTotal = Quantity.ZERO;
        Money grIr = Money.zero(currency);
        for (GoodsReceiptMatch match : line.matches()) {
            GoodsReceiptLine receiptLine = receiptLines.findById(match.goodsReceiptLineId())
                    .orElseThrow(() -> GoodsReceiptNotFoundException.forLine(
                            match.goodsReceiptLineId()));
            requireMatchable(receiptLine, match, supplier, product, currency);

            matched.add(new MatchedQuantity(receiptLine, match.quantity()));
            matchedTotal = matchedTotal.plus(match.quantity());
            grIr = grIr.plus(
                    receiptLine.getUnitCost().extend(match.quantity(), roundingMode));
        }
        if (matchedTotal.compareTo(line.quantity()) > 0) {
            throw new InvalidPurchaseInvoiceException(
                    "This line invoices " + line.quantity() + " and claims to be paying for "
                            + matchedTotal + " already delivered. An invoice cannot settle more goods "
                            + "than it charges for — GR/IR would clear stock nobody was billed for.");
        }

        // The unmatched half: goods invoiced and not yet received, held in GR/IR at the invoice's own
        // price until a delivery credits it back. ADR 0004's other direction.
        Quantity open = line.quantity().minus(matchedTotal);
        if (open.isPositive()) {
            grIr = grIr.plus(line.unitPrice().extend(open, roundingMode));
        }

        // ADR 0008: the residual, so the line's debits sum exactly to what the supplier charged. It
        // absorbs both the real price difference and any cent of rounding between the two valuations,
        // which is the right place for a cent to land — an unbalanced invoice would be refused by the
        // ledger, and the variance account is where a difference belongs by construction.
        Money variance = net.minus(grIr);

        return CostedLine.inventory(line, product, net, vat, grIr, variance, matched);
    }

    private static void requireReverseChargeAgreesWithSupplier(
            NewPurchaseInvoiceLine line, SupplierView supplier) {
        if (line.isExempt()) {
            return;
        }
        boolean intraEu = supplier.vatStatus() == VatStatus.INTRA_EU_B2B;
        if (intraEu && !line.reverseCharge()) {
            throw new InvalidPurchaseInvoiceException(
                    "Supplier '" + supplier.name() + "' is " + supplier.vatStatus() + ", so a "
                            + "VAT-classed line from them is a self-assessed intra-EU acquisition and "
                            + "must be reverse-charged. Posting input VAT the supplier never charged "
                            + "would reclaim tax nobody paid.");
        }
        if (!intraEu && line.reverseCharge()) {
            throw new InvalidPurchaseInvoiceException(
                    "Supplier '" + supplier.name() + "' is " + supplier.vatStatus() + ", so this line "
                            + "cannot be reverse-charged: they charged us Greek VAT and we reclaim it. "
                            + "Self-assessing as well would declare output VAT on a purchase.");
        }
    }

    private void requireMatchable(GoodsReceiptLine receiptLine, GoodsReceiptMatch match,
            SupplierView supplier, ProductView product, Currency currency) {
        GoodsReceipt receipt = receiptLine.getReceipt();
        if (!Objects.equals(receipt.getSupplierId(), supplier.id())) {
            throw new InvalidPurchaseInvoiceException(
                    "Goods receipt line " + receiptLine.getId() + " came from a different supplier. "
                            + "One supplier's invoice cannot clear another's delivery, or the GR/IR "
                            + "balance stops meaning anything per supplier.");
        }
        if (receipt.isReversal()) {
            throw new InvalidPurchaseInvoiceException(
                    "Goods receipt line " + receiptLine.getId() + " belongs to a reversing document, "
                            + "which un-made a delivery rather than making one.");
        }
        if (!Objects.equals(receiptLine.getProductId(), product.id())) {
            throw new InvalidPurchaseInvoiceException(
                    "This line invoices '" + product.sku() + "' but is matched to a delivery of a "
                            + "different product. Matching them would clear GR/IR for goods that never "
                            + "arrived and leave the ones that did sitting open.");
        }
        if (!receiptLine.getUnitCost().currency().equals(currency)) {
            throw new InvalidPurchaseInvoiceException(
                    "Goods receipt line " + receiptLine.getId() + " was received in "
                            + receiptLine.getUnitCost().currency().getCurrencyCode()
                            + " and this invoice is in " + currency.getCurrencyCode()
                            + ". NovoCore does not convert (ADR 0005).");
        }
        Quantity alreadyMatched =
                Quantity.of(matches.matchedAgainstReceiptLine(receiptLine.getId()));
        Quantity stillOpen = receiptLine.getQuantity().minus(alreadyMatched);
        if (match.quantity().compareTo(stillOpen) > 0) {
            throw new InvalidPurchaseInvoiceException(
                    "Goods receipt line " + receiptLine.getId() + " delivered "
                            + receiptLine.getQuantity() + ", of which " + alreadyMatched
                            + " is already invoiced, so " + match.quantity() + " cannot be matched "
                            + "against it. Invoicing the same delivery twice would clear GR/IR below "
                            + "zero and pay for goods once too often.");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    /**
     * Debit GR/IR (inventory lines) or the named account (expense lines), debit input VAT per class,
     * credit output VAT per class for reverse-charged lines, credit accounts payable with the gross.
     *
     * <p>The variance line is what makes each inventory line sum to its net, so the entry balances by
     * construction rather than by arithmetic that has to be got right twice.
     */
    private gr.novotrade.novocore.core.api.ledger.JournalEntryView post(NewPurchaseInvoice request,
            SupplierView supplier, List<CostedLine> costed, Currency currency) {
        AccountView grIrAccount = chartOfAccounts.requireAccount(
                AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING);
        AccountView varianceAccount =
                chartOfAccounts.requireAccount(AccountSystemKey.PURCHASE_PRICE_VARIANCE);
        AccountView inputVat = chartOfAccounts.requireAccount(AccountSystemKey.INPUT_VAT);
        AccountView outputVat = chartOfAccounts.requireAccount(AccountSystemKey.OUTPUT_VAT);
        AccountView payable = chartOfAccounts.requireAccount(AccountSystemKey.ACCOUNTS_PAYABLE);
        SubLedgerRef supplierRef = SubLedgerRef.supplier(supplier.id());

        List<NewJournalLine> lines = new ArrayList<>();
        Money net = Money.zero(currency);
        Money vatCharged = Money.zero(currency);

        // Q14: per line, then summed by class. Two maps rather than one, because reverse charge posts
        // the same figure on both sides and only one of them is money the supplier asked for.
        Map<Long, Money> inputByClass = new LinkedHashMap<>();
        Map<Long, Money> inputBaseByClass = new LinkedHashMap<>();
        Map<Long, Money> outputByClass = new LinkedHashMap<>();
        Map<Long, Money> outputBaseByClass = new LinkedHashMap<>();

        for (CostedLine line : costed) {
            net = net.plus(line.net());

            if (line.isInventory()) {
                if (line.grIr().isPositive()) {
                    lines.add(NewJournalLine.debit(grIrAccount.id(), line.grIr())
                            .forSubLedger(supplierRef)
                            .describedAs(line.narrative()));
                }
                if (!line.variance().isZero()) {
                    lines.add(line.variance().isPositive()
                            ? NewJournalLine.debit(varianceAccount.id(), line.variance())
                                    .describedAs(line.narrative())
                            : NewJournalLine.credit(varianceAccount.id(), line.variance().abs())
                                    .describedAs(line.narrative()));
                }
            } else if (line.net().isPositive()) {
                lines.add(NewJournalLine.debit(line.expenseAccountId(), line.net())
                        .describedAs(line.narrative()));
            }

            if (line.vat().isPositive()) {
                long classId = line.vatClassId();
                inputByClass.merge(classId, line.vat(), Money::plus);
                inputBaseByClass.merge(classId, line.net(), Money::plus);
                if (line.reverseCharge()) {
                    outputByClass.merge(classId, line.vat(), Money::plus);
                    outputBaseByClass.merge(classId, line.net(), Money::plus);
                } else {
                    vatCharged = vatCharged.plus(line.vat());
                }
            }
        }

        inputByClass.forEach((classId, amount) -> lines.add(
                NewJournalLine.debit(inputVat.id(), amount)
                        .withVat(VatDimension.of(classId, inputBaseByClass.get(classId)))));
        outputByClass.forEach((classId, amount) -> lines.add(
                NewJournalLine.credit(outputVat.id(), amount)
                        .withVat(VatDimension.of(classId, outputBaseByClass.get(classId)))
                        .describedAs("Reverse charge — self-assessed")));

        lines.add(NewJournalLine.credit(payable.id(), net.plus(vatCharged))
                .forSubLedger(supplierRef));

        String description = request.description() != null
                ? request.description()
                : "Purchase invoice " + request.supplierInvoiceNumber() + " — " + supplier.name();

        return journal.post(NewJournalEntry.of(
                request.invoiceDate(), description, JournalSource.PURCHASE_INVOICE, lines));
    }

    // ---------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------

    private PurchaseInvoice load(long invoiceId) {
        return invoices.findById(invoiceId)
                .orElseThrow(() -> new PurchaseInvoiceNotFoundException(invoiceId));
    }

    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no invoices in it.");
        }
    }

    private PurchaseInvoiceView toView(PurchaseInvoice invoice) {
        SupplierView supplier = suppliers.require(invoice.getSupplierId());
        List<PurchaseInvoiceLineView> lineViews =
                invoice.getLines().stream().map(this::toView).toList();

        Currency currency = lineViews.isEmpty()
                ? Money.EUR : lineViews.getFirst().netAmount().currency();
        Money netTotal = Money.zero(currency);
        Money vatTotal = Money.zero(currency);
        Money grossTotal = Money.zero(currency);
        Money variance = Money.zero(currency);
        for (PurchaseInvoiceLineView line : lineViews) {
            netTotal = netTotal.plus(line.netAmount());
            vatTotal = vatTotal.plus(line.vatAmount());
            grossTotal = grossTotal.plus(line.grossAmount());
        }
        for (PurchaseInvoiceLine line : invoice.getLines()) {
            variance = variance.plus(line.getVarianceAmount());
        }

        return new PurchaseInvoiceView(
                invoice.getId(),
                supplier.id(),
                supplier.name(),
                invoice.getSupplierInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getDescription(),
                netTotal,
                vatTotal,
                grossTotal,
                variance,
                invoice.getJournalEntryId(),
                invoice.getReversalOfId(),
                invoices.findByReversalOfId(invoice.getId())
                        .map(PurchaseInvoice::getId).orElse(null),
                lineViews);
    }

    private PurchaseInvoiceLineView toView(PurchaseInvoiceLine line) {
        Quantity matchedQuantity = line.isInventory()
                ? Quantity.of(matches.matchedAgainstInvoiceLine(line.getId()))
                : Quantity.ZERO;
        String sku = line.getProductId() == null
                ? null : productService.require(line.getProductId()).sku();
        return PurchaseInvoiceLineViews.of(line, sku, matchedQuantity);
    }

    private GrIrMatchView toView(GrIrMatch match) {
        GoodsReceiptLine receiptLine = match.getReceiptLine();
        return new GrIrMatchView(
                match.getId(),
                match.getInvoiceLine().getId(),
                receiptLine.getId(),
                inventory.findLotByReceiptLine(receiptLine.getId())
                        .map(InventoryLotView::id)
                        .orElseThrow(() -> new IllegalStateException(
                                "Goods receipt line " + receiptLine.getId() + " created no lot. Every "
                                        + "line does, by ADR 0004, so this is a delivery recorded "
                                        + "outside GoodsReceiptService.")),
                match.getQuantity(),
                receiptLine.getUnitCost(),
                match.getInvoiceLine().getUnitPrice());
    }

    // ---------------------------------------------------------------------------------------
    // Internals — one line's arithmetic, carried from validation through to posting
    // ---------------------------------------------------------------------------------------

    private record MatchedQuantity(GoodsReceiptLine receiptLine, Quantity quantity) {
    }

    /**
     * One request line with its figures worked out: what it charged, what VAT it carried, how much of
     * it clears GR/IR and at what, and what is left over as variance.
     *
     * <p>Computed once and carried, rather than recomputed for validation and again for posting. The
     * second computation is where the two would drift apart.
     */
    private record CostedLine(
            NewPurchaseInvoiceLine request,
            ProductView product,
            AccountView expenseAccount,
            Money net,
            Money vat,
            Money grIr,
            Money variance,
            List<MatchedQuantity> matches) {

        static CostedLine inventory(NewPurchaseInvoiceLine request, ProductView product, Money net,
                Money vat, Money grIr, Money variance, List<MatchedQuantity> matches) {
            return new CostedLine(request, product, null, net, vat, grIr, variance, matches);
        }

        static CostedLine expense(NewPurchaseInvoiceLine request, AccountView account, Money net,
                Money vat) {
            return new CostedLine(request, null, account, net, vat,
                    Money.zero(net.currency()), Money.zero(net.currency()), List.of());
        }

        boolean isInventory() {
            return request.isInventory();
        }

        long expenseAccountId() {
            return expenseAccount.id();
        }

        long vatClassId() {
            return request.vatClassId();
        }

        boolean reverseCharge() {
            return request.reverseCharge();
        }

        /** Net plus the VAT the supplier actually asked for — reverse charge asks for none. */
        Money gross() {
            return request.reverseCharge() ? net : net.plus(vat);
        }

        String narrative() {
            if (request.description() != null) {
                return request.description();
            }
            return isInventory()
                    ? request.quantity() + " x " + product.sku()
                    : expenseAccount.name();
        }

        PurchaseInvoiceLine toEntity() {
            return isInventory()
                    ? PurchaseInvoiceLine.inventory(product.id(), request.quantity(),
                            request.unitPrice(), net, vat, request.vatClassId(),
                            request.vatExemptionReasonId(), request.reverseCharge(), variance,
                            request.description())
                    : PurchaseInvoiceLine.expense(expenseAccount.id(), net, vat,
                            request.vatClassId(), request.vatExemptionReasonId(),
                            request.reverseCharge(), request.description());
        }
    }
}
