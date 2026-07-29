package gr.novotrade.novocore.core.settlement;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceView;
import gr.novotrade.novocore.core.api.sales.CreditNoteService;
import gr.novotrade.novocore.core.api.sales.CreditNoteView;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.settlement.AllocationSourceType;
import gr.novotrade.novocore.core.api.settlement.AllocationView;
import gr.novotrade.novocore.core.api.settlement.CustomerCreditView;
import gr.novotrade.novocore.core.api.settlement.InvalidSettlementException;
import gr.novotrade.novocore.core.api.settlement.NewAllocation;
import gr.novotrade.novocore.core.api.settlement.NewSettlement;
import gr.novotrade.novocore.core.api.settlement.OpenItem;
import gr.novotrade.novocore.core.api.settlement.OpenItemRef;
import gr.novotrade.novocore.core.api.settlement.OpenItemType;
import gr.novotrade.novocore.core.api.settlement.PartyType;
import gr.novotrade.novocore.core.api.settlement.SettlementDirection;
import gr.novotrade.novocore.core.api.settlement.SettlementNotFoundException;
import gr.novotrade.novocore.core.api.settlement.SettlementService;
import gr.novotrade.novocore.core.api.settlement.SettlementView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipts, Payments, and the open-item layer over Accounts receivable and Accounts payable.
 *
 * <p><strong>Documents post; allocations do not.</strong> The invoice posted, the receipt posted, and
 * saying which one paid the other would debit and credit the same control account for the same amount.
 * Everything unusual about this class follows from that: an allocation is created, reduced and released
 * freely without touching a posted entry, and nothing anywhere stores an open amount or a "paid" flag.
 *
 * <p>Documents in other slices are reached through their published services, which is the only route
 * available — their entities are package-private (ADR 0003 inside the core as well as around it).
 */
@Service
class SettlementServiceImpl implements SettlementService {

    private static final String ENTITY_TYPE = "Settlement";
    private static final String CREDIT_ENTITY_TYPE = "CustomerCredit";
    private static final String ALLOCATION_ENTITY_TYPE = "OpenItemAllocation";

    /** The whole of recorded history, for the listings that have no date range of their own. */
    private static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);
    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    private final SettlementRepository settlements;
    private final OpenItemAllocationRepository allocations;
    private final CustomerCreditRepository customerCredits;
    private final CustomerService customers;
    private final SupplierService suppliers;
    private final SalesInvoiceService salesInvoices;
    private final CreditNoteService creditNotes;
    private final PurchaseInvoiceService purchaseInvoices;
    private final ChartOfAccountsService chartOfAccounts;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    SettlementServiceImpl(SettlementRepository settlements,
            OpenItemAllocationRepository allocations, CustomerCreditRepository customerCredits,
            CustomerService customers, SupplierService suppliers, SalesInvoiceService salesInvoices,
            CreditNoteService creditNotes, PurchaseInvoiceService purchaseInvoices,
            ChartOfAccountsService chartOfAccounts, JournalService journal, SettingsService settings,
            AuditLogService auditLog) {
        this.settlements = settlements;
        this.allocations = allocations;
        this.customerCredits = customerCredits;
        this.customers = customers;
        this.suppliers = suppliers;
        this.salesInvoices = salesInvoices;
        this.creditNotes = creditNotes;
        this.purchaseInvoices = purchaseInvoices;
        this.chartOfAccounts = chartOfAccounts;
        this.journal = journal;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Recording money moving
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public SettlementView record(NewSettlement request) {
        Objects.requireNonNull(request, "request");
        String partyName = requireActiveParty(request.partyType(), request.partyId());
        AccountView account = requireMovableAccount(request.accountId());
        requireWithinCashLimit(account, request.amount());

        long entryId = post(request, account, partyName).id();

        Settlement settlement = new Settlement(request.direction(), request.partyType(),
                request.partyId(), account.id(), request.settlementDate(), request.amount(),
                request.reference(), request.description());
        settlement.postedAs(entryId);
        Settlement saved = settlements.save(settlement);
        settlements.flush();

        applyAllocations(saved, request.allocations(), 0);

        if (request.remainderBecomesCustomerCredit()) {
            leaveRemainderAsCredit(saved, request.description());
        }

        auditLog.record("settlement.recorded", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "direction", saved.getDirection().name(),
                "party", saved.getPartyType() + " " + partyName,
                "account", account.name(),
                "settlementDate", saved.getSettlementDate().toString(),
                "amount", saved.getAmount().toString(),
                "allocations", String.valueOf(request.allocations().size()),
                "journalEntry", String.valueOf(entryId)));

        return toView(saved);
    }

    @Override
    @Transactional
    public SettlementView amend(long settlementId, long accountId, LocalDate settlementDate,
            Money amount, String reference, String description) {
        Objects.requireNonNull(settlementDate, "settlementDate");
        Objects.requireNonNull(amount, "amount");
        Settlement settlement = load(settlementId);

        if (!amount.isPositive()) {
            throw new InvalidSettlementException(
                    "Settlement amount " + amount + " is not positive. Which way the money went is "
                            + "said by the direction, not by the sign.");
        }
        if (!amount.currency().equals(settlement.getAmount().currency())) {
            throw new InvalidSettlementException(
                    "Settlement " + settlementId + " is in "
                            + settlement.getAmount().currency().getCurrencyCode()
                            + " and the amendment is in " + amount.currency().getCurrencyCode()
                            + ". NovoCore does not convert (ADR 0005).");
        }
        AccountView account = requireMovableAccount(accountId);
        requireWithinCashLimit(account, amount);

        String partyName = partyNameOf(settlement.getPartyType(), settlement.getPartyId());

        // Q13's mechanism: the previous state goes to the audit log BEFORE it is overwritten, which is
        // what makes "editable in place" leave a trail rather than replace one.
        auditLog.record("settlement.amended", ENTITY_TYPE, String.valueOf(settlementId), Map.of(
                "previousAccountId", String.valueOf(settlement.getAccountId()),
                "previousDate", settlement.getSettlementDate().toString(),
                "previousAmount", settlement.getAmount().toString(),
                "previousReference", settlement.getReference() == null
                        ? "(none)" : settlement.getReference(),
                "newAccountId", String.valueOf(accountId),
                "newDate", settlementDate.toString(),
                "newAmount", amount.toString()));

        releaseAllocationsDownTo(settlement, amount);

        settlement.amend(accountId, settlementDate,
                amount, blankToNull(reference), blankToNull(description));

        journal.amend(settlement.getJournalEntryId(), settlementDate,
                describe(settlement, partyName),
                journalLinesFor(settlement.getDirection(), settlement.getPartyType(),
                        settlement.getPartyId(), account, amount, partyName));

        return toView(settlement);
    }

    /**
     * <strong>Q13's second half.</strong> Reducing a settlement below what it has already been
     * allocated to releases allocations starting with the most recently applied one and working
     * backward, reducing the last one partially if that is enough.
     *
     * <p>Most-recent-first because the earlier allocations are the ones somebody deliberately matched;
     * releasing those and keeping the newest would undo a decision in favour of an accident of
     * ordering. Every release is recorded in the audit log with the allocation it touched.
     */
    private void releaseAllocationsDownTo(Settlement settlement, Money newAmount) {
        Money allocated = allocatedFrom(AllocationSourceType.SETTLEMENT, settlement.getId(),
                newAmount.currency());
        if (allocated.compareTo(newAmount) <= 0) {
            return;
        }

        Money excess = allocated.minus(newAmount);
        List<OpenItemAllocation> applied = new ArrayList<>(
                allocations.findBySourceTypeAndSourceIdOrderByAllocationOrderAsc(
                        AllocationSourceType.SETTLEMENT, settlement.getId()));
        applied.sort(Comparator.comparingInt(OpenItemAllocation::getAllocationOrder).reversed());

        for (OpenItemAllocation allocation : applied) {
            if (!excess.isPositive()) {
                break;
            }
            if (allocation.getAmount().compareTo(excess) <= 0) {
                auditLog.record("settlement.allocation-released", ALLOCATION_ENTITY_TYPE,
                        String.valueOf(allocation.getId()), Map.of(
                                "settlement", String.valueOf(settlement.getId()),
                                "target", allocation.getTarget().toString(),
                                "amount", allocation.getAmount().toString(),
                                "reason", "settlement reduced below its allocated total"));
                excess = excess.minus(allocation.getAmount());
                allocations.delete(allocation);
            } else {
                Money reduced = allocation.getAmount().minus(excess);
                auditLog.record("settlement.allocation-reduced", ALLOCATION_ENTITY_TYPE,
                        String.valueOf(allocation.getId()), Map.of(
                                "settlement", String.valueOf(settlement.getId()),
                                "target", allocation.getTarget().toString(),
                                "from", allocation.getAmount().toString(),
                                "to", reduced.toString(),
                                "reason", "settlement reduced below its allocated total"));
                allocation.reduceTo(reduced);
                excess = Money.zero(excess.currency());
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Allocating, without moving money
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public SettlementView allocate(long settlementId, List<NewAllocation> newAllocations) {
        Objects.requireNonNull(newAllocations, "allocations");
        Settlement settlement = load(settlementId);

        int nextOrder = allocations.findBySourceTypeAndSourceIdOrderByAllocationOrderAsc(
                        AllocationSourceType.SETTLEMENT, settlementId).stream()
                .mapToInt(OpenItemAllocation::getAllocationOrder)
                .max()
                .orElse(-1) + 1;

        applyAllocations(settlement, newAllocations, nextOrder);
        return toView(settlement);
    }

    @Override
    @Transactional
    public AllocationView allocateCreditNote(
            long creditNoteId, long salesInvoiceId, Money amount) {
        CreditNoteView note = creditNotes.require(creditNoteId);
        SalesInvoiceView invoice = salesInvoices.require(salesInvoiceId);
        if (note.customerId() != invoice.customerId()) {
            throw new InvalidSettlementException(
                    "Credit note " + creditNoteId + " belongs to " + note.customerName()
                            + " and sales invoice " + salesInvoiceId + " to " + invoice.customerName()
                            + ". One customer's return cannot settle another's invoice.");
        }
        return allocateNonCash(AllocationSourceType.CREDIT_NOTE, creditNoteId,
                OpenItemRef.creditNote(creditNoteId), OpenItemRef.salesInvoice(salesInvoiceId),
                amount);
    }

    @Override
    @Transactional
    public AllocationView allocateCustomerCredit(
            long customerCreditId, long salesInvoiceId, Money amount) {
        CustomerCredit credit = customerCredits.findById(customerCreditId)
                .orElseThrow(() -> new InvalidSettlementException(
                        "No customer credit with id " + customerCreditId + "."));
        SalesInvoiceView invoice = salesInvoices.require(salesInvoiceId);
        if (!Objects.equals(credit.getCustomerId(), invoice.customerId())) {
            throw new InvalidSettlementException(
                    "Customer credit " + customerCreditId + " belongs to a different customer from "
                            + "sales invoice " + salesInvoiceId + ". One customer's credit balance "
                            + "cannot settle another's invoice.");
        }

        Money remaining = openCreditAmount(credit);
        if (amount.compareTo(remaining) > 0) {
            throw new InvalidSettlementException(
                    "Customer credit " + customerCreditId + " has " + remaining + " left and cannot "
                            + "supply " + amount + ". Spending credit twice would settle an invoice "
                            + "with money the customer does not have here.");
        }
        return allocateNonCash(AllocationSourceType.CUSTOMER_CREDIT, customerCreditId, null,
                OpenItemRef.salesInvoice(salesInvoiceId), amount);
    }

    /**
     * The shared half of the two allocations that move no money: a credit note or customer credit set
     * against an invoice. Both ends are Accounts receivable, so nothing posts.
     */
    private AllocationView allocateNonCash(AllocationSourceType sourceType, long sourceId,
            OpenItemRef sourceRef, OpenItemRef target, Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new InvalidSettlementException(
                    "Allocation amount " + amount + " is not positive. Un-allocating is releasing the "
                            + "allocation, not adding a negative one.");
        }
        if (sourceRef != null) {
            Money sourceOpen = openAmount(sourceRef);
            if (amount.compareTo(sourceOpen) > 0) {
                throw new InvalidSettlementException(
                        sourceRef + " has " + sourceOpen + " left to apply and cannot supply "
                                + amount + ".");
            }
        }
        Money targetOpen = openAmount(target);
        if (amount.compareTo(targetOpen) > 0) {
            throw new InvalidSettlementException(
                    target + " has " + targetOpen + " outstanding and cannot take an allocation of "
                            + amount + ". Settling a document beyond its open amount would leave it "
                            + "reading as overpaid while the money went somewhere else.");
        }

        int nextOrder = allocations.findBySourceTypeAndSourceIdOrderByAllocationOrderAsc(
                        sourceType, sourceId).stream()
                .mapToInt(OpenItemAllocation::getAllocationOrder)
                .max()
                .orElse(-1) + 1;

        OpenItemAllocation saved = allocations.save(
                new OpenItemAllocation(sourceType, sourceId, target, nextOrder, amount));

        auditLog.record("settlement.allocated", ALLOCATION_ENTITY_TYPE,
                String.valueOf(saved.getId()), Map.of(
                        "source", sourceType + "#" + sourceId,
                        "target", target.toString(),
                        "amount", amount.toString(),
                        "posted", "false"));

        return toView(saved);
    }

    @Override
    @Transactional
    public void release(long allocationId) {
        OpenItemAllocation allocation = allocations.findById(allocationId)
                .orElseThrow(() -> new InvalidSettlementException(
                        "No allocation with id " + allocationId + "."));

        // Deleted rather than reversed, which is the one place in this schema that happens — and it is
        // right here for the reason the rest refuses it: an allocation is not a record of an event, it
        // is a statement about the current relationship between two documents. Nothing was posted for
        // it, so nothing is being erased from the ledger.
        auditLog.record("settlement.allocation-released", ALLOCATION_ENTITY_TYPE,
                String.valueOf(allocationId), Map.of(
                        "source", allocation.getSourceType() + "#" + allocation.getSourceId(),
                        "target", allocation.getTarget().toString(),
                        "amount", allocation.getAmount().toString(),
                        "reason", "released explicitly"));
        allocations.delete(allocation);
    }

    private void applyAllocations(
            Settlement settlement, List<NewAllocation> requested, int startingOrder) {
        if (requested.isEmpty()) {
            return;
        }
        Money available = settlement.getAmount().minus(
                allocatedFrom(AllocationSourceType.SETTLEMENT, settlement.getId(),
                        settlement.getAmount().currency()));

        int order = startingOrder;
        for (NewAllocation allocation : requested) {
            Money amount = allocation.amount();
            if (!amount.currency().equals(settlement.getAmount().currency())) {
                throw new InvalidSettlementException(
                        "This settlement is in "
                                + settlement.getAmount().currency().getCurrencyCode()
                                + " and an allocation is in " + amount.currency().getCurrencyCode()
                                + ". NovoCore does not convert (ADR 0005).");
            }
            if (amount.compareTo(available) > 0) {
                throw new InvalidSettlementException(
                        "This settlement has " + available + " left to apply and cannot allocate "
                                + amount + " to " + allocation.target() + ". Allocating more than the "
                                + "money that moved would settle invoices nobody paid for.");
            }
            requireAllocatable(settlement, allocation.target(), amount);

            allocations.save(new OpenItemAllocation(AllocationSourceType.SETTLEMENT,
                    settlement.getId(), allocation.target(), order++, amount));
            available = available.minus(amount);

            auditLog.record("settlement.allocated", ALLOCATION_ENTITY_TYPE,
                    String.valueOf(settlement.getId()), Map.of(
                            "source", "SETTLEMENT#" + settlement.getId(),
                            "target", allocation.target().toString(),
                            "amount", amount.toString(),
                            "posted", "false"));
        }
    }

    /**
     * Whether this settlement may settle that document.
     *
     * <p>Four legal combinations, and the fifth — money received from a supplier — has nothing to
     * target, because NovoCore has no supplier credit note. It stays unallocated and queryable, which
     * is the honest answer rather than inventing a document to point at.
     */
    private void requireAllocatable(Settlement settlement, OpenItemRef target, Money amount) {
        if (!settlement.getPartyType().owns(target.type())) {
            throw new InvalidSettlementException(
                    "A " + settlement.getPartyType() + " settlement cannot settle a " + target.type()
                            + ": the two sit behind different control accounts, so the allocation "
                            + "would claim one sub-ledger was settled by the other.");
        }

        OpenItemType expected = expectedTargetFor(settlement);
        if (expected == null) {
            throw new InvalidSettlementException(
                    "Money received from a supplier has nothing to be allocated against: NovoCore "
                            + "records no supplier credit note, so a refund from them sits "
                            + "unallocated against their Accounts payable balance until the next "
                            + "invoice, which is what it actually is.");
        }
        if (target.type() != expected) {
            throw new InvalidSettlementException(
                    "A " + settlement.getDirection() + " " + settlement.getPartyType()
                            + " settlement settles a " + expected + ", not a " + target.type() + ".");
        }

        long targetParty = partyOf(target);
        if (targetParty != settlement.getPartyId()) {
            throw new InvalidSettlementException(
                    target + " belongs to a different " + settlement.getPartyType()
                            + ". One party's money cannot settle another's document — the sub-ledger "
                            + "balance is the whole point of a control account.");
        }

        Money open = openAmount(target);
        if (amount.compareTo(open) > 0) {
            throw new InvalidSettlementException(
                    target + " has " + open + " outstanding and cannot take an allocation of "
                            + amount + (open.isZero()
                            ? ". A sales invoice paid in cash or through a partner clearing account is "
                                    + "born fully settled and never has an open amount."
                            : ". Settling a document beyond its open amount would leave it reading as "
                                    + "overpaid while the money went somewhere else."));
        }
    }

    private static OpenItemType expectedTargetFor(Settlement settlement) {
        if (settlement.getPartyType() == PartyType.SUPPLIER) {
            return settlement.getDirection() == SettlementDirection.OUTGOING
                    ? OpenItemType.PURCHASE_INVOICE : null;
        }
        return settlement.getDirection() == SettlementDirection.INCOMING
                ? OpenItemType.SALES_INVOICE : OpenItemType.CREDIT_NOTE;
    }

    /**
     * Q16: what is left of a receipt becomes a standalone credit document, when the caller says so.
     *
     * <p>Only when they say so. The alternative reading — a bulk remittance nobody has finished
     * matching — is equally real, and guessing between them is what rule 7 forbids.
     */
    private void leaveRemainderAsCredit(Settlement settlement, String description) {
        Money remainder = settlement.getAmount().minus(
                allocatedFrom(AllocationSourceType.SETTLEMENT, settlement.getId(),
                        settlement.getAmount().currency()));
        if (!remainder.isPositive()) {
            return;
        }

        CustomerCredit credit = customerCredits.save(new CustomerCredit(settlement.getPartyId(),
                settlement.getId(), settlement.getSettlementDate(), remainder, description));

        auditLog.record("customer-credit.created", CREDIT_ENTITY_TYPE,
                String.valueOf(credit.getId()), Map.of(
                        "customer", String.valueOf(settlement.getPartyId()),
                        "settlement", String.valueOf(settlement.getId()),
                        "amount", remainder.toString()));
    }

    // ---------------------------------------------------------------------------------------
    // Open items — computed on every read
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Money openAmountOf(OpenItemRef ref) {
        return openAmount(ref);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OpenItem> openItem(OpenItemRef ref) {
        return findOpenItem(ref);
    }

    /**
     * The open amount, without going through the proxy.
     *
     * <p>The public {@link #openAmountOf} is the transactional entry point for callers outside
     * this class; this is what the allocation checks inside it use. Separated because those checks
     * run from private helpers, and a private helper calling {@code openAmountOf} would be a
     * self-invocation — harmless here, since the public methods that reach those helpers are all
     * transactional and the read simply joins that transaction, but indistinguishable in the
     * bytecode from the shape that is <em>not</em> harmless. Splitting the two says which is the
     * entry point and which is the computation, and keeps the ArchUnit rule sharp enough to be
     * worth having.
     */
    private Money openAmount(OpenItemRef ref) {
        return findOpenItem(ref).map(OpenItem::openAmount).orElse(Money.zero(Money.EUR));
    }

    private Optional<OpenItem> findOpenItem(OpenItemRef ref) {
        Objects.requireNonNull(ref, "ref");
        return switch (ref.type()) {
            case SALES_INVOICE -> salesInvoices.find(ref.id()).map(this::toOpenItem);
            case CREDIT_NOTE -> creditNotes.find(ref.id()).map(this::toOpenItem);
            case PURCHASE_INVOICE -> purchaseInvoices.find(ref.id()).map(this::toOpenItem);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenItem> openItemsFor(PartyType partyType, long partyId) {
        Objects.requireNonNull(partyType, "partyType");
        List<OpenItem> items = new ArrayList<>();
        if (partyType == PartyType.CUSTOMER) {
            salesInvoices.ofCustomer(partyId).forEach(invoice -> items.add(toOpenItem(invoice)));
            creditNotes.ofCustomer(partyId).forEach(note -> items.add(toOpenItem(note)));
        } else {
            purchaseInvoices.ofSupplier(partyId).forEach(invoice -> items.add(toOpenItem(invoice)));
        }
        return outstanding(items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenItem> allOpenItems(PartyType partyType) {
        Objects.requireNonNull(partyType, "partyType");
        List<OpenItem> items = new ArrayList<>();
        if (partyType == PartyType.CUSTOMER) {
            salesInvoices.between(BEGINNING, FOREVER)
                    .forEach(invoice -> items.add(toOpenItem(invoice)));
            creditNotes.between(BEGINNING, FOREVER).forEach(note -> items.add(toOpenItem(note)));
        } else {
            purchaseInvoices.between(BEGINNING, FOREVER)
                    .forEach(invoice -> items.add(toOpenItem(invoice)));
        }
        return outstanding(items);
    }

    private static List<OpenItem> outstanding(List<OpenItem> items) {
        return items.stream()
                .filter(item -> !item.isFullySettled())
                .sorted(Comparator.comparing(OpenItem::documentDate)
                        .thenComparing(item -> item.ref().id()))
                .toList();
    }

    private OpenItem toOpenItem(SalesInvoiceView invoice) {
        // Born settled means there never was an open amount: cash and the partner clearing methods
        // debit their own account, so nothing sits against Accounts receivable to be settled.
        Money gross = invoice.isInForce() && !invoice.bornSettled()
                ? invoice.grossTotal()
                : Money.zero(invoice.grossTotal().currency());
        return openItemOf(OpenItemRef.salesInvoice(invoice.id()), invoice.documentNumber(),
                invoice.invoiceDate(), invoice.customerId(), invoice.customerName(), gross,
                Money.zero(gross.currency()));
    }

    /**
     * A credit note is the one open item that can be settled from <em>either</em> side, so both count
     * against what is left of it.
     *
     * <p>It is a target when a refund pays it out, and a source when it is set against an invoice
     * instead. Counting only one would let the same credit be both refunded in cash and applied to an
     * invoice — the customer paid twice, with each half looking correct on its own.
     */
    private OpenItem toOpenItem(CreditNoteView note) {
        Money gross = note.isInForce()
                ? note.grossTotal() : Money.zero(note.grossTotal().currency());
        Money spentAgainstInvoices = allocatedFrom(
                AllocationSourceType.CREDIT_NOTE, note.id(), gross.currency());
        return openItemOf(OpenItemRef.creditNote(note.id()), note.documentNumber(),
                note.creditNoteDate(), note.customerId(), note.customerName(), gross,
                spentAgainstInvoices);
    }

    private OpenItem toOpenItem(PurchaseInvoiceView invoice) {
        Money gross = invoice.isInForce()
                ? invoice.grossTotal() : Money.zero(invoice.grossTotal().currency());
        return openItemOf(OpenItemRef.purchaseInvoice(invoice.id()),
                invoice.supplierInvoiceNumber(), invoice.invoiceDate(), invoice.supplierId(),
                invoice.supplierName(), gross, Money.zero(gross.currency()));
    }

    private OpenItem openItemOf(OpenItemRef ref, String documentNumber, LocalDate documentDate,
            long partyId, String partyName, Money gross, Money alsoSpentAsASource) {
        Money allocated = Money.of(
                        allocations.allocatedAgainst(ref.type(), ref.id()), gross.currency())
                .plus(alsoSpentAsASource);
        return new OpenItem(ref, documentNumber, documentDate, partyId, partyName, gross, allocated,
                gross.minus(allocated));
    }

    private long partyOf(OpenItemRef ref) {
        return switch (ref.type()) {
            case SALES_INVOICE -> salesInvoices.require(ref.id()).customerId();
            case CREDIT_NOTE -> creditNotes.require(ref.id()).customerId();
            case PURCHASE_INVOICE -> purchaseInvoices.require(ref.id()).supplierId();
        };
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementView> find(long settlementId) {
        return settlements.findById(settlementId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementView require(long settlementId) {
        return toView(load(settlementId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementView> ofParty(PartyType partyType, long partyId) {
        Objects.requireNonNull(partyType, "partyType");
        return settlements.findByPartyTypeAndPartyIdOrderBySettlementDateAscIdAsc(partyType, partyId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementView> between(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with nothing received or paid.");
        }
        return settlements.findBySettlementDateBetweenOrderBySettlementDateAscIdAsc(from, to).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementView> findByJournalEntry(long journalEntryId) {
        return settlements.findByJournalEntryId(journalEntryId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementView> withUnallocatedAmount() {
        return settlements.findWithUnallocatedAmount(AllocationSourceType.SETTLEMENT).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCreditView> customerCreditsOf(long customerId) {
        return customerCredits.findByCustomerIdOrderByCreditDateAscIdAsc(customerId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCreditView> openCustomerCredits() {
        return customerCredits.findAllByOrderByCreditDateAscIdAsc().stream()
                .map(this::toView)
                .filter(credit -> !credit.isExhausted())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationView> allocationsAgainst(OpenItemRef ref) {
        Objects.requireNonNull(ref, "ref");
        return allocations.findByTargetTypeAndTargetIdOrderByAllocationOrderAsc(ref.type(), ref.id())
                .stream()
                .map(this::toView)
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    private gr.novotrade.novocore.core.api.ledger.JournalEntryView post(
            NewSettlement request, AccountView account, String partyName) {
        return journal.post(NewJournalEntry.of(
                request.settlementDate(),
                request.description() != null
                        ? request.description()
                        : describe(request.direction(), partyName, request.reference()),
                request.direction().journalSource(),
                journalLinesFor(request.direction(), request.partyType(), request.partyId(), account,
                        request.amount(), partyName)));
    }

    private List<NewJournalLine> journalLinesFor(SettlementDirection direction, PartyType partyType,
            long partyId, AccountView account, Money amount, String partyName) {
        AccountView control = chartOfAccounts.requireAccount(partyType.controlAccount());
        NewJournalLine ourSide = direction.isIncoming()
                ? NewJournalLine.debit(account.id(), amount)
                : NewJournalLine.credit(account.id(), amount);
        NewJournalLine theirSide = direction.isIncoming()
                ? NewJournalLine.credit(control.id(), amount)
                : NewJournalLine.debit(control.id(), amount);
        return List.of(
                ourSide.describedAs(partyName),
                theirSide.forSubLedger(partyType.refTo(partyId)));
    }

    private static String describe(
            SettlementDirection direction, String partyName, String reference) {
        return (direction.isIncoming() ? "Receipt from " : "Payment to ") + partyName
                + (reference == null ? "" : " — " + reference);
    }

    private String describe(Settlement settlement, String partyName) {
        return settlement.getDescription() != null
                ? settlement.getDescription()
                : describe(settlement.getDirection(), partyName, settlement.getReference());
    }

    // ---------------------------------------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------------------------------------

    private String requireActiveParty(PartyType partyType, long partyId) {
        if (partyType == PartyType.CUSTOMER) {
            var customer = customers.require(partyId);
            if (!customer.active()) {
                throw new InvalidSettlementException(
                        "Customer '" + customer.name() + "' is inactive. Money can still be received "
                                + "from them in reality, so reactivate them rather than recording it "
                                + "against somebody else.");
            }
            return customer.name();
        }
        var supplier = suppliers.require(partyId);
        if (!supplier.active()) {
            throw new InvalidSettlementException(
                    "Supplier '" + supplier.name() + "' is inactive. An outstanding balance is still "
                            + "payable, so reactivate them rather than paying somebody else.");
        }
        return supplier.name();
    }

    private String partyNameOf(PartyType partyType, long partyId) {
        return partyType == PartyType.CUSTOMER
                ? customers.require(partyId).name()
                : suppliers.require(partyId).name();
    }

    /**
     * The account money actually moved through has to be one money can be held in.
     *
     * <p>A transfer into an expense account is not a settlement, and letting one through here would be
     * a payment recorded as something else — which is how money leaves the business without appearing
     * to.
     */
    private AccountView requireMovableAccount(long accountId) {
        AccountView account = chartOfAccounts.requireAccount(accountId);
        if (!account.active()) {
            throw new InvalidSettlementException(
                    "Account '" + account.name() + "' is inactive, so nothing new may post to it.");
        }
        if (account.kind() != AccountKind.BANK_CASH
                && account.kind() != AccountKind.PARTNER_CLEARING) {
            throw new InvalidSettlementException(
                    "Account '" + account.name() + "' is " + account.kind() + ", so money cannot be "
                            + "received into or paid out of it. A settlement moves money through a "
                            + "bank account, the cash box, or a partner clearing account.");
        }
        return account;
    }

    private void requireWithinCashLimit(AccountView account, Money amount) {
        if (account.systemKey() != AccountSystemKey.CASH) {
            return;
        }
        Money limit = settings.requireEurAmount(SettingKeys.CASH_PAYMENT_LIMIT);
        if (amount.compareTo(limit) >= 0) {
            throw new InvalidSettlementException(
                    "A cash movement of " + amount + " reaches the legal cash limit of " + limit
                            + " and is blocked. Under N. 5301/2026 the penalty runs to double the "
                            + "cash amount, so this is refused rather than flagged.");
        }
    }

    private Money allocatedFrom(AllocationSourceType sourceType, long sourceId,
            java.util.Currency currency) {
        return Money.of(allocations.allocatedFrom(sourceType, sourceId), currency);
    }

    private Money openCreditAmount(CustomerCredit credit) {
        return credit.getAmount().minus(allocatedFrom(AllocationSourceType.CUSTOMER_CREDIT,
                credit.getId(), credit.getAmount().currency()));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // ---------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------

    private Settlement load(long settlementId) {
        return settlements.findById(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException(settlementId));
    }

    private SettlementView toView(Settlement settlement) {
        AccountView account = chartOfAccounts.requireAccount(settlement.getAccountId());
        Money allocated = allocatedFrom(AllocationSourceType.SETTLEMENT, settlement.getId(),
                settlement.getAmount().currency());

        List<AllocationView> allocationViews =
                allocations.findBySourceTypeAndSourceIdOrderByAllocationOrderAsc(
                                AllocationSourceType.SETTLEMENT, settlement.getId()).stream()
                        .map(this::toView)
                        .toList();

        return new SettlementView(
                settlement.getId(),
                settlement.getDirection(),
                settlement.getPartyType(),
                settlement.getPartyId(),
                partyNameOf(settlement.getPartyType(), settlement.getPartyId()),
                account.id(),
                account.name(),
                settlement.getSettlementDate(),
                settlement.getAmount(),
                allocated,
                settlement.getAmount().minus(allocated),
                settlement.getReference(),
                settlement.getDescription(),
                settlement.getJournalEntryId(),
                customerCredits.findBySettlementId(settlement.getId())
                        .map(CustomerCredit::getId).orElse(null),
                allocationViews);
    }

    private AllocationView toView(OpenItemAllocation allocation) {
        return new AllocationView(
                allocation.getId(),
                allocation.getSourceType(),
                allocation.getSourceId(),
                allocation.getTarget(),
                allocation.getAllocationOrder(),
                allocation.getAmount());
    }

    private CustomerCreditView toView(CustomerCredit credit) {
        return new CustomerCreditView(
                credit.getId(),
                credit.getCustomerId(),
                customers.require(credit.getCustomerId()).name(),
                credit.getSettlementId(),
                credit.getCreditDate(),
                credit.getAmount(),
                openCreditAmount(credit),
                credit.getDescription());
    }
}
