package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.ledger.VatDimension;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.sales.CreditNoteLineView;
import gr.novotrade.novocore.core.api.sales.CreditNoteNotFoundException;
import gr.novotrade.novocore.core.api.sales.CreditNotePreview;
import gr.novotrade.novocore.core.api.sales.CreditNotePreviewLine;
import gr.novotrade.novocore.core.api.sales.CreditNoteService;
import gr.novotrade.novocore.core.api.sales.CreditNoteView;
import gr.novotrade.novocore.core.api.sales.InvalidCreditNoteException;
import gr.novotrade.novocore.core.api.sales.NewCreditNote;
import gr.novotrade.novocore.core.api.sales.NewCreditNoteLine;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
 * Credit notes — <strong>Q26 as a transaction rather than a policy</strong>.
 *
 * <p>Every line credits a specific invoice line, which is what supplies the VAT class, the product and
 * the channel. The rate is <em>copied</em> from the sale rather than re-resolved: a credit note issued
 * after the customer's VAT override changed must still return the VAT that was actually charged, or a
 * return at 13% nets against an output at 24% and the VAT return stops reconciling.
 *
 * <h2>Where the credit goes — reversed in step 15, deliberately</h2>
 *
 * <p><strong>The credit now mirrors what the invoice debited:</strong> the settlement account for a
 * born-settled sale, {@code Accounts receivable} otherwise. Until step 15 it always went to AR, and
 * the reason given was a real one, so it is kept here rather than deleted: <em>"the money is owed
 * back to the customer until it is refunded, and posting the credit straight against the cash box
 * would take money out of the till that nobody handed over."</em>
 *
 * <p>That argument was overruled on evidence rather than on taste. A born-settled invoice never
 * debits AR, and {@code bornSettled} keeps it out of the open-item layer entirely — so a credit note
 * that <em>did</em> move AR made the Accounts Receivable control account disagree with the sum of
 * its open items, which ADR 0009 states is impossible by construction. Step 15's HTTP narrative
 * measured the disagreement; nothing before it had credited a cash, POS or Skroutz sale.
 *
 * <p>Mirroring closes the class of defect structurally instead of correcting a figure: <strong>
 * neither half of a born-settled transaction ever touches AR or the open-item layer</strong>, so
 * there is nothing left for them to disagree about. It is also what happens in the world — a
 * refunded card sale is refunded to the card, not booked as a receivable somebody must chase.
 *
 * <p>The till objection survives as a real operational point and is answered elsewhere: a credit
 * note is a document, and the cash actually leaving the drawer is a Refund settlement
 * ({@code NewSettlement.refundTo}). What changed is only which account the <em>document</em>
 * credits, not whether a refund has to be recorded.
 */
@Service
class CreditNoteServiceImpl implements CreditNoteService {

    private static final String ENTITY_TYPE = "CreditNote";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CreditNoteRepository creditNotes;
    private final SalesInvoiceRepository invoices;
    private final SalesInvoiceLineRepository invoiceLines;
    private final CustomerService customers;
    private final ProductService products;
    private final ChartOfAccountsService chartOfAccounts;
    private final VatClassService vatClasses;
    private final InventoryService inventory;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    CreditNoteServiceImpl(CreditNoteRepository creditNotes, SalesInvoiceRepository invoices,
            SalesInvoiceLineRepository invoiceLines, CustomerService customers,
            ProductService products, ChartOfAccountsService chartOfAccounts,
            VatClassService vatClasses, InventoryService inventory, JournalService journal,
            SettingsService settings, AuditLogService auditLog) {
        this.creditNotes = creditNotes;
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.customers = customers;
        this.products = products;
        this.chartOfAccounts = chartOfAccounts;
        this.vatClasses = vatClasses;
        this.inventory = inventory;
        this.journal = journal;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------

    /**
     * Everything {@link #record} works out before it writes anything.
     *
     * <p>Extracted so {@link #preview} produces its figures <strong>from this code</strong> rather
     * than from a second implementation. Same arrangement, and same reasoning, as
     * {@code SalesInvoiceServiceImpl.compute}: every refusal about the request lives here, and the
     * single decision left to the caller is what to do about a rounding difference above the
     * threshold — {@code record} refuses it, {@code preview} reports it so a screen can offer the
     * acceptance before the operator submits.
     */
    private Computation compute(NewCreditNote request) {
        Objects.requireNonNull(request, "request");
        SalesInvoice invoice = invoices.findById(request.salesInvoiceId())
                .orElseThrow(() -> new SalesInvoiceNotFoundException(request.salesInvoiceId()));

        if (invoice.isReversal() || invoices.findByReversalOfId(invoice.getId()).isPresent()) {
            throw new InvalidCreditNoteException(
                    "Sales invoice " + invoice.getId() + " has been reversed, so as far as the "
                            + "records are concerned the sale never happened. There is nothing to "
                            + "credit.");
        }
        if (creditNotes.existsStandingCreditNote(request.documentNumber())) {
            throw new InvalidCreditNoteException(
                    "Credit note '" + request.documentNumber() + "' has already been issued. Issuing "
                            + "it twice would return the same VAT twice.");
        }

        CustomerView customer = customers.require(invoice.getCustomerId());
        Currency currency = invoice.getRoundingAmount().currency();
        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);

        List<CreditedLine> credited = new ArrayList<>();
        for (NewCreditNoteLine line : request.lines()) {
            credited.add(credit(line, invoice, currency, roundingMode));
        }

        Money computedGross = Money.zero(currency);
        for (CreditedLine line : credited) {
            computedGross = computedGross.plus(line.gross());
        }
        if (!computedGross.isPositive()) {
            throw new InvalidCreditNoteException(
                    "This credit note comes to " + computedGross + ", so it credits nothing.");
        }

        Rounding rounding = compareAgainstDocument(request, computedGross, currency);
        Money payable = computedGross.plus(rounding.amount());

        return new Computation(invoice, customer, credited, computedGross, rounding, payable,
                currency);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditNotePreview preview(NewCreditNote request) {
        Computation computation = compute(request);

        List<CreditNotePreviewLine> lines = new ArrayList<>();
        Money net = Money.zero(computation.currency());
        Money vat = Money.zero(computation.currency());
        for (CreditedLine line : computation.credited()) {
            lines.add(line.toPreviewLine());
            net = net.plus(line.net());
            vat = vat.plus(line.vat());
        }

        return new CreditNotePreview(
                lines, net, vat, computation.computedGross(),
                request.statedTotal(),
                computation.rounding().amount(),
                computation.rounding().threshold(),
                computation.rounding().needsAcceptance(),
                computation.payable());
    }

    @Override
    @Transactional
    public CreditNoteView record(NewCreditNote request) {
        Computation computation = compute(request);
        SalesInvoice invoice = computation.invoice();
        CustomerView customer = computation.customer();
        List<CreditedLine> credited = computation.credited();
        Rounding rounding = computation.rounding();
        Money payable = computation.payable();
        Currency currency = computation.currency();

        // The one refusal compute() reports rather than raises, so that preview can show the
        // difference and offer the acceptance. Issuing is where it becomes a refusal.
        if (rounding.needsAcceptance()) {
            throw new InvalidCreditNoteException(
                    "This credit note's lines come to " + computation.computedGross()
                            + " and the document states " + request.statedTotal()
                            + ", a difference of " + rounding.amount() + ". That is more than the "
                            + "rounding threshold of " + rounding.threshold() + ", so somebody has "
                            + "to look at it — fix the lines, or record who accepts the difference "
                            + "and why.");
        }

        long entryId = post(request, invoice, customer, credited, rounding, payable, currency).id();

        CreditNote note = new CreditNote(invoice.getId(), customer.id(), request.documentNumber(),
                request.creditNoteDate(), request.description(), request.statedTotal(),
                rounding.amount(), rounding.neededReview(), rounding.acceptedBy(),
                rounding.acceptedAt(), request.roundingNote(), null);
        note.postedAs(entryId);
        for (CreditedLine line : credited) {
            note.addLine(line.toEntity());
        }
        CreditNote saved = creditNotes.save(note);
        creditNotes.flush();

        returnStock(saved, credited, request.creditNoteDate());

        auditLog.record("credit-note.issued", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "customer", customer.name(),
                "number", saved.getDocumentNumber(),
                "againstInvoice", String.valueOf(invoice.getId()),
                "creditNoteDate", saved.getCreditNoteDate().toString(),
                "gross", payable.toString(),
                "rounding", rounding.amount().toString(),
                "lines", String.valueOf(credited.size()),
                "journalEntry", String.valueOf(entryId)));

        return toView(saved);
    }

    @Override
    @Transactional
    public CreditNoteView reverse(long creditNoteId, LocalDate reversalDate, String reason) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        CreditNote original = load(creditNoteId);

        if (original.isReversal()) {
            throw new InvalidCreditNoteException(
                    "Credit note " + creditNoteId + " is itself the reversal of credit note "
                            + original.getReversalOfId() + ".");
        }
        creditNotes.findByReversalOfId(creditNoteId).ifPresent(existing -> {
            throw new InvalidCreditNoteException(
                    "Credit note " + creditNoteId + " has already been reversed by document "
                            + existing.getId() + ".");
        });
        if (original.getLines().stream().anyMatch(CreditNoteLine::isStockReturned)) {
            // ADR 0008's principle, applied to the other direction: a posting that reflects a
            // physically verified event is not un-made once other things depend on it. Goods that came
            // back are on a shelf, in a lot FIFO can already have consumed from. If they have gone out
            // again, that is a sale.
            throw new InvalidCreditNoteException(
                    "Credit note " + creditNoteId + " brought stock back, so it is not reversible: "
                            + "the goods are physically on a shelf and in a lot that may already have "
                            + "been sold from again. A credit note whose money was wrong is corrected "
                            + "by a fresh sales invoice for what is actually owed; goods that went "
                            + "back out are a sale.");
        }

        long reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                original.getJournalEntryId(),
                reversalDate,
                "Reversal of credit note " + creditNoteId + " (" + original.getDocumentNumber() + ")"
                        + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()),
                JournalSource.CREDIT_NOTE,
                journal.mirrorOf(original.getJournalEntryId()))).id();

        CreditNote reversal = new CreditNote(original.getSalesInvoiceId(), original.getCustomerId(),
                original.getDocumentNumber(), reversalDate,
                reason == null || reason.isBlank() ? null : reason.trim(), null,
                Money.zero(original.getRoundingAmount().currency()), false, null, null, null,
                creditNoteId);
        reversal.postedAs(reversingEntryId);
        CreditNote saved = creditNotes.save(reversal);

        auditLog.record("credit-note.reversed", ENTITY_TYPE, String.valueOf(creditNoteId), Map.of(
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
    public Optional<CreditNoteView> find(long creditNoteId) {
        return creditNotes.findById(creditNoteId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditNoteView require(long creditNoteId) {
        return toView(load(creditNoteId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditNoteView> againstInvoice(long salesInvoiceId) {
        return creditNotes.findBySalesInvoiceIdOrderByIdAsc(salesInvoiceId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditNoteView> ofCustomer(long customerId) {
        return creditNotes.findByCustomerIdOrderByCreditNoteDateAscIdAsc(customerId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditNoteView> between(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no credit notes in it.");
        }
        return creditNotes.findByCreditNoteDateBetweenOrderByCreditNoteDateAscIdAsc(from, to).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditNoteView> findByJournalEntry(long journalEntryId) {
        return creditNotes.findByJournalEntryId(journalEntryId).map(this::toView);
    }

    // ---------------------------------------------------------------------------------------
    // Crediting one line
    // ---------------------------------------------------------------------------------------

    private CreditedLine credit(NewCreditNoteLine line, SalesInvoice invoice, Currency currency,
            RoundingMode roundingMode) {
        SalesInvoiceLine invoiceLine = invoiceLines.findById(line.salesInvoiceLineId())
                .orElseThrow(() -> SalesInvoiceNotFoundException.forLine(line.salesInvoiceLineId()));

        if (!Objects.equals(invoiceLine.getInvoice().getId(), invoice.getId())) {
            throw new InvalidCreditNoteException(
                    "Line " + line.salesInvoiceLineId() + " belongs to sales invoice "
                            + invoiceLine.getInvoice().getId() + ", not to " + invoice.getId()
                            + ". A credit note credits one sale, and crediting across documents "
                            + "would make either invoice's remaining balance unreadable.");
        }
        if (!line.unitPrice().currency().equals(currency)) {
            throw new InvalidCreditNoteException(
                    "This credit note is in " + currency.getCurrencyCode() + " and a line is in "
                            + line.unitPrice().currency().getCurrencyCode()
                            + ". NovoCore does not convert (ADR 0005).");
        }

        Quantity alreadyCredited =
                Quantity.of(creditNotes.creditedQuantityOf(invoiceLine.getId()));
        Quantity remaining = invoiceLine.getQuantity().minus(alreadyCredited);
        if (line.quantity().compareTo(remaining) > 0) {
            throw new InvalidCreditNoteException(
                    "Invoice line " + invoiceLine.getId() + " sold " + invoiceLine.getQuantity()
                            + " and " + alreadyCredited + " has already been credited, so "
                            + line.quantity() + " cannot be. Crediting more than was sold would "
                            + "reclaim output VAT that was never charged.");
        }
        if (line.unitPrice().compareTo(invoiceLine.getUnitPrice()) > 0) {
            throw new InvalidCreditNoteException(
                    "Invoice line " + invoiceLine.getId() + " sold at " + invoiceLine.getUnitPrice()
                            + " and this line credits " + line.unitPrice()
                            + ". Crediting above what was charged is a payment to the customer, not a "
                            + "return, and it would post revenue backwards.");
        }
        if (line.stockReturned() && invoiceLine.getStockConsumptionId() == null
                && invoiceLine.getComponents().stream()
                        .allMatch(component -> component.getStockConsumptionId() == null)) {
            throw new InvalidCreditNoteException(
                    "Invoice line " + invoiceLine.getId() + " took no stock out — it is a service, a "
                            + "charge, or goods that were never in a lot — so nothing can come back "
                            + "into stock against it. Credit it without returning stock.");
        }

        Money net = Money.rounded(line.netExactly(), currency, roundingMode);
        Money vat = Money.zero(currency);
        if (invoiceLine.getVatClassId() != null) {
            // The rate the SALE charged, never re-resolved: a credit note issued after the customer's
            // override changed must still return the VAT that was actually collected.
            VatClassView vatClass = vatClasses.require(invoiceLine.getVatClassId());
            vat = Money.rounded(net.amount().multiply(vatClass.ratePercent().percent()).divide(ONE_HUNDRED),
                    currency, roundingMode);
        }

        return new CreditedLine(line, invoiceLine, net, vat);
    }

    private Rounding compareAgainstDocument(
            NewCreditNote request, Money computedGross, Currency currency) {
        Money threshold = settings.requireEurAmount(SettingKeys.LEDGER_ROUNDING_THRESHOLD);

        Money stated = request.statedTotal();
        if (stated == null) {
            return Rounding.none(currency, threshold);
        }
        if (!stated.currency().equals(currency)) {
            throw new InvalidCreditNoteException(
                    "This credit note is priced in " + currency.getCurrencyCode() + " and its stated "
                            + "total is in " + stated.currency().getCurrencyCode()
                            + ". NovoCore does not convert (ADR 0005).");
        }

        Money difference = stated.minus(computedGross);
        if (difference.isZero()) {
            return Rounding.none(currency, threshold);
        }

        if (difference.abs().compareTo(threshold) <= 0) {
            return Rounding.automatic(difference, threshold);
        }
        if (!request.roundingDifferenceIsAccepted()) {
            // Reported, not thrown — see compute(). record() raises it.
            return Rounding.unaccepted(difference, threshold);
        }
        return Rounding.accepted(difference, request.roundingAcceptedBy(), threshold);
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    /**
     * Debit the channel's {@code Sales returns} account with the net, debit Output VAT per class, and
     * credit back <strong>whatever the invoice debited</strong> — the settlement account for a
     * born-settled sale, Accounts receivable otherwise.
     *
     * <p><strong>Step 15 changed this half, and it was a real defect.</strong> A credit note used to
     * credit Accounts receivable unconditionally, including against a cash, POS or Skroutz sale that
     * had never debited AR in the first place — those methods settle immediately and debit their own
     * account (see {@code SalesInvoiceServiceImpl.post}). The two sides of one born-settled
     * transaction were therefore asymmetric: the sale bypassed AR and its correction did not.
     *
     * <p>The consequence was not cosmetic. Such an invoice is {@code bornSettled}, so the open-item
     * layer excludes it entirely, while the credit note against it still moved AR — which made the
     * Accounts Receivable control account and the sum of the open items disagree, an outcome ADR
     * 0009 says is impossible by construction. It was found by step 15's HTTP narrative, the first
     * thing to credit a Skroutz sale; nothing before it had put a credit note against a born-settled
     * invoice.
     *
     * <p>Mirroring the invoice closes the whole class structurally rather than correcting one
     * figure: <strong>neither a born-settled invoice nor its credit note ever touches AR or the
     * open-item layer</strong>, so there is nothing left to reconcile between them. The money going
     * back to the customer comes out of the account it went into, which is also what actually
     * happens — a refunded card sale is refunded to the card.
     *
     * <p>A sub-ledger reference goes on the AR line only. AR is a Control account and requires one;
     * the settlement accounts are Bank-Cash or Partner Clearing and take none, which is the same
     * asymmetry {@code SalesInvoiceServiceImpl} already has on the debit side.
     */
    private JournalEntryView post(NewCreditNote request, SalesInvoice invoice, CustomerView customer,
            List<CreditedLine> credited, Rounding rounding, Money payable, Currency currency) {
        AccountView returns =
                chartOfAccounts.requireAccount(invoice.getChannel().returnsAccount());
        AccountView outputVat = chartOfAccounts.requireAccount(AccountSystemKey.OUTPUT_VAT);
        Optional<AccountSystemKey> settlementAccount =
                invoice.getSettlementMethod().settlementAccount();

        List<NewJournalLine> lines = new ArrayList<>();
        Money netTotal = Money.zero(currency);
        Map<Long, Money> vatByClass = new LinkedHashMap<>();
        Map<Long, Money> baseByClass = new LinkedHashMap<>();

        for (CreditedLine line : credited) {
            netTotal = netTotal.plus(line.net());
            if (line.vat().isPositive()) {
                long classId = line.invoiceLine().getVatClassId();
                vatByClass.merge(classId, line.vat(), Money::plus);
                baseByClass.merge(classId, line.net(), Money::plus);
            }
        }

        // One debit to the channel's returns account rather than one per line: the whole document is
        // one channel's return, and which lines it was is on the document.
        lines.add(NewJournalLine.debit(returns.id(), netTotal)
                .describedAs("Credit note " + request.documentNumber()));

        vatByClass.forEach((classId, amount) -> lines.add(
                NewJournalLine.debit(outputVat.id(), amount)
                        .withVat(VatDimension.of(classId, baseByClass.get(classId)))));

        if (!rounding.amount().isZero()) {
            AccountView roundingAccount =
                    chartOfAccounts.requireAccount(AccountSystemKey.ROUNDING_DIFFERENCES);
            lines.add(rounding.amount().isPositive()
                    ? NewJournalLine.debit(roundingAccount.id(), rounding.amount())
                            .describedAs("Rounding against document total")
                    : NewJournalLine.credit(roundingAccount.id(), rounding.amount().abs())
                            .describedAs("Rounding against document total"));
        }

        if (settlementAccount.isPresent()) {
            lines.add(NewJournalLine
                    .credit(chartOfAccounts.requireAccount(settlementAccount.get()).id(), payable)
                    .describedAs(invoice.getSettlementMethod() + " refund — " + customer.name()));
        } else {
            lines.add(NewJournalLine
                    .credit(chartOfAccounts.requireAccount(
                            AccountSystemKey.ACCOUNTS_RECEIVABLE).id(), payable)
                    .forSubLedger(SubLedgerRef.customer(customer.id())));
        }

        String description = request.description() != null
                ? request.description()
                : "Credit note " + request.documentNumber() + " against invoice "
                        + invoice.getDocumentNumber() + " — " + customer.name();

        return journal.post(NewJournalEntry.of(
                request.creditNoteDate(), description, JournalSource.CREDIT_NOTE, lines));
    }

    // ---------------------------------------------------------------------------------------
    // Putting the stock back
    // ---------------------------------------------------------------------------------------

    private void returnStock(CreditNote note, List<CreditedLine> credited, LocalDate returnDate) {
        List<CreditNoteLine> savedLines = note.getLines();
        for (int i = 0; i < credited.size(); i++) {
            CreditedLine line = credited.get(i);
            if (!line.request().stockReturned()) {
                continue;
            }
            SalesInvoiceLine invoiceLine = line.invoiceLine();

            if (invoiceLine.getStockConsumptionId() != null) {
                StockConsumptionView returned = inventory.returnConsumed(
                        invoiceLine.getStockConsumptionId(), line.request().quantity(), returnDate,
                        "Credit note " + note.getDocumentNumber());
                savedLines.get(i).returnedAs(returned.costedNothing() ? null : returned.id());
                continue;
            }

            // A bundle returns at component level, in the same proportion it was sold. The share is
            // computed exactly and refuses rather than rounding: half a component coming back is not
            // a physical fact, and a rounded quantity would leave the lot disagreeing with what went
            // out.
            for (SalesInvoiceLineComponent component : invoiceLine.getComponents()) {
                if (component.getStockConsumptionId() == null) {
                    continue;
                }
                Quantity share = proportionOf(component.getQuantity(),
                        line.request().quantity(), invoiceLine.getQuantity(),
                        invoiceLine.getId());
                inventory.returnConsumed(component.getStockConsumptionId(), share, returnDate,
                        "Credit note " + note.getDocumentNumber());
            }
            // A bundle line's own consumption id stays null: the stock came back at component level,
            // which is where it left from, and pointing this line at one of several component
            // consumptions would name an arbitrary one of them.
            savedLines.get(i).returnedAs(null);
        }
    }

    private static Quantity proportionOf(Quantity componentTotal, Quantity creditedBundles,
            Quantity soldBundles, long invoiceLineId) {
        if (creditedBundles.compareTo(soldBundles) == 0) {
            return componentTotal;
        }
        try {
            BigDecimal share = componentTotal.value()
                    .multiply(creditedBundles.value())
                    .divide(soldBundles.value(), Quantity.SCALE, RoundingMode.UNNECESSARY);
            return Quantity.of(share);
        } catch (ArithmeticException rounded) {
            throw new InvalidCreditNoteException(
                    "Returning " + creditedBundles + " of the " + soldBundles + " bundles sold on "
                            + "invoice line " + invoiceLineId + " would put back a fraction of a "
                            + "component. Half a component coming back is not a physical fact, so "
                            + "credit the price without returning stock, or return whole bundles.");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------

    private CreditNote load(long creditNoteId) {
        return creditNotes.findById(creditNoteId)
                .orElseThrow(() -> new CreditNoteNotFoundException(creditNoteId));
    }

    private CreditNoteView toView(CreditNote note) {
        SalesInvoice invoice = invoices.findById(note.getSalesInvoiceId()).orElseThrow();
        CustomerView customer = customers.require(note.getCustomerId());
        List<CreditNoteLineView> lineViews = note.getLines().stream().map(this::toView).toList();

        Currency currency = note.getRoundingAmount().currency();
        Money netTotal = Money.zero(currency);
        Money vatTotal = Money.zero(currency);
        for (CreditNoteLineView line : lineViews) {
            netTotal = netTotal.plus(line.netAmount());
            vatTotal = vatTotal.plus(line.vatAmount());
        }

        return new CreditNoteView(
                note.getId(),
                invoice.getId(),
                invoice.getDocumentNumber(),
                customer.id(),
                customer.name(),
                invoice.getChannel(),
                invoice.getSettlementMethod(),
                note.getDocumentNumber(),
                note.getCreditNoteDate(),
                note.getDescription(),
                netTotal,
                vatTotal,
                netTotal.plus(vatTotal).plus(note.getRoundingAmount()),
                note.getStatedTotal(),
                note.getRoundingAmount(),
                note.isRoundingNeededReview(),
                note.getRoundingAcceptedBy(),
                note.getRoundingAcceptedAt(),
                note.getRoundingNote(),
                note.getJournalEntryId(),
                note.getReversalOfId(),
                creditNotes.findByReversalOfId(note.getId()).map(CreditNote::getId).orElse(null),
                lineViews);
    }

    private CreditNoteLineView toView(CreditNoteLine line) {
        SalesInvoiceLine invoiceLine =
                invoiceLines.findById(line.getSalesInvoiceLineId()).orElseThrow();
        return new CreditNoteLineView(
                line.getId(),
                line.getLineNumber(),
                line.getSalesInvoiceLineId(),
                invoiceLine.getProductId(),
                invoiceLine.getProductId() == null
                        ? null : products.require(invoiceLine.getProductId()).sku(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getNetAmount(),
                line.getVatAmount(),
                invoiceLine.getVatClassId(),
                invoiceLine.getVatExemptionReasonId(),
                line.isStockReturned(),
                line.getReturnConsumptionId(),
                line.getDescription());
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    /**
     * The outcome of comparing the lines against the document, and the threshold used.
     *
     * @param needsAcceptance the difference exceeds the threshold and nobody has accepted it. Not a
     *     refusal in itself — see {@link #compute}.
     */
    private record Rounding(
            Money amount, boolean neededReview, String acceptedBy, Instant acceptedAt,
            Money threshold, boolean needsAcceptance) {

        static Rounding none(Currency currency, Money threshold) {
            return new Rounding(Money.zero(currency), false, null, null, threshold, false);
        }

        static Rounding automatic(Money difference, Money threshold) {
            return new Rounding(difference, false, null, null, threshold, false);
        }

        static Rounding accepted(Money difference, String acceptedBy, Money threshold) {
            return new Rounding(difference, true, acceptedBy, Instant.now(), threshold, false);
        }

        static Rounding unaccepted(Money difference, Money threshold) {
            return new Rounding(difference, true, null, null, threshold, true);
        }
    }

    /**
     * Everything worked out from a request, before anything is written.
     *
     * <p>The single value {@link #record} and {@link #preview} share, which is what makes "the
     * preview cannot drift from the posting" a property of the code rather than a promise.
     */
    private record Computation(
            SalesInvoice invoice,
            CustomerView customer,
            List<CreditedLine> credited,
            Money computedGross,
            Rounding rounding,
            Money payable,
            Currency currency) {
    }

    private record CreditedLine(
            NewCreditNoteLine request, SalesInvoiceLine invoiceLine, Money net, Money vat) {

        Money gross() {
            return net.plus(vat);
        }

        /**
         * The same figures the entity would carry, for a caller who is not posting.
         *
         * <p>The product or charge and the VAT class come from the <em>invoice</em> line, which is
         * the point: a credit gives back what the sale took, at the rate it charged, and neither is
         * re-derived from anything current.
         */
        CreditNotePreviewLine toPreviewLine() {
            return new CreditNotePreviewLine(
                    invoiceLine.getId(),
                    invoiceLine.getProductId(),
                    invoiceLine.getChargeTypeId(),
                    request.description(),
                    request.quantity(),
                    request.unitPrice(),
                    net, vat, gross(),
                    invoiceLine.getVatClassId(),
                    request.stockReturned());
        }

        CreditNoteLine toEntity() {
            return new CreditNoteLine(invoiceLine.getId(), request.quantity(), request.unitPrice(),
                    net, vat, request.stockReturned(), request.description());
        }
    }
}
