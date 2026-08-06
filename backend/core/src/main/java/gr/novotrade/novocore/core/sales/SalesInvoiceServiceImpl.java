package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.bundle.BundleComponentLine;
import gr.novotrade.novocore.core.api.bundle.BundleDecomposition;
import gr.novotrade.novocore.core.api.bundle.BundleService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeView;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesView;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeService;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.inventory.SaleReference;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.ledger.VatDimension;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.sales.InvalidSalesInvoiceException;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoiceLine;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceLineComponentView;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceLineView;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.sales.SalesInvoicePreview;
import gr.novotrade.novocore.core.api.sales.SalesInvoicePreviewLine;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceSort;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassPrecedence;
import gr.novotrade.novocore.core.api.tax.VatClassResolution;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.customer.CustomerSearch;
import gr.novotrade.novocore.core.document.SalesDocumentSeriesSearch;
import gr.novotrade.novocore.core.support.SpringPaging;
import gr.novotrade.novocore.core.support.TextSearch;
import org.springframework.data.jpa.domain.Specification;
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
 * Recording sales, and the two entries each one produces.
 *
 * <p><strong>Revenue here, cost in {@code InventoryService}.</strong> A sale posts its revenue entry
 * in this class and its cost of goods sold in another, because consuming lots without posting is the
 * "half is worse than neither" problem the write-off settled. The two are linked by the consumption
 * record each line points at.
 *
 * <p><strong>The VAT dimension is supplied here</strong> — the step 7 obligation. Every Output VAT
 * line carries the class it was computed at and the base it was computed from, per line and summed by
 * class, so a VAT return assembled from the ledger can tell two rates apart instead of finding one
 * indistinguishable total.
 */
@Service
class SalesInvoiceServiceImpl implements SalesInvoiceService {

    private static final String ENTITY_TYPE = "SalesInvoice";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final SalesInvoiceRepository invoices;
    private final CreditNoteRepository creditNotes;
    private final CustomerService customers;
    private final SalesDocumentSeriesService salesSeries;
    private final SalesDocumentTypeService salesDocumentTypes;
    private final ProductService products;
    private final BundleService bundles;
    private final ChargeTypeService chargeTypes;
    private final ChartOfAccountsService chartOfAccounts;
    private final VatClassService vatClasses;
    private final VatExemptionReasonService exemptionReasons;
    private final InventoryService inventory;
    private final JournalService journal;
    private final PaymentMethodService paymentMethods;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    SalesInvoiceServiceImpl(SalesInvoiceRepository invoices, CreditNoteRepository creditNotes,
            CustomerService customers, SalesDocumentSeriesService salesSeries,
            SalesDocumentTypeService salesDocumentTypes,
            ProductService products, BundleService bundles,
            ChargeTypeService chargeTypes, ChartOfAccountsService chartOfAccounts,
            VatClassService vatClasses, VatExemptionReasonService exemptionReasons,
            InventoryService inventory, JournalService journal,
            PaymentMethodService paymentMethods, SettingsService settings,
            AuditLogService auditLog) {
        this.invoices = invoices;
        this.creditNotes = creditNotes;
        this.customers = customers;
        this.salesSeries = salesSeries;
        this.salesDocumentTypes = salesDocumentTypes;
        this.products = products;
        this.bundles = bundles;
        this.chargeTypes = chargeTypes;
        this.chartOfAccounts = chartOfAccounts;
        this.vatClasses = vatClasses;
        this.exemptionReasons = exemptionReasons;
        this.inventory = inventory;
        this.journal = journal;
        this.paymentMethods = paymentMethods;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------

    /**
     * Everything {@link #record} works out before it writes anything.
     *
     * <p>Extracted so {@link #preview} can produce the same figures <strong>from the same code</strong>
     * rather than from a second implementation that agrees until it does not. Every refusal that is
     * about the request rather than about posting lives here, so a preview refuses what a record
     * would refuse — which is most of what a preview is for.
     *
     * <p>The one thing that is <em>not</em> decided here is what to do about a rounding difference
     * above the threshold. {@link Rounding#needsAcceptance()} reports it and the caller decides:
     * {@code record} refuses, {@code preview} returns it so an entry screen can offer the acceptance
     * control before the operator submits. Deciding it here would make that screen impossible to
     * build without a second way to compute the difference, which is the thing this method exists to
     * prevent.
     */
    private Computation compute(NewSalesInvoice request) {
        Objects.requireNonNull(request, "request");
        SeriesContext series = resolveSeries(request.seriesId());
        CustomerView customer = customers.require(request.customerId());
        if (!customer.active()) {
            throw new InvalidSalesInvoiceException(
                    "Customer '" + customer.name() + "' is inactive, so nothing new should be sold to "
                            + "them. A customer is deactivated precisely so that stops.");
        }
        if (invoices.existsStandingInvoice(request.seriesId(), request.documentNumber())) {
            throw new InvalidSalesInvoiceException(
                    "Sales invoice '" + request.documentNumber() + "' has already been recorded in "
                            + "series '" + series.series().abbreviation() + "'. "
                            + "Recording it twice would state the same revenue and the same output VAT "
                            + "twice. Reverse the one already recorded if it was wrong; a reversed "
                            + "invoice releases its number.");
        }

        Currency currency = request.lines().getFirst().unitPrice().currency();
        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);

        List<PricedLine> priced = new ArrayList<>();
        for (NewSalesInvoiceLine line : request.lines()) {
            if (!line.unitPrice().currency().equals(currency)) {
                throw new InvalidSalesInvoiceException(
                        "This invoice mixes " + currency.getCurrencyCode() + " and "
                                + line.unitPrice().currency().getCurrencyCode() + ". NovoCore does "
                                + "not convert between currencies and will not silently pick one "
                                + "(ADR 0005), so an invoice spanning two has no total to post.");
            }
            priced.add(price(line, customer, series.channel(), currency, roundingMode));
        }

        Money computedGross = Money.zero(currency);
        for (PricedLine line : priced) {
            computedGross = computedGross.plus(line.gross());
        }
        if (!computedGross.isPositive()) {
            throw new InvalidSalesInvoiceException(
                    "This invoice comes to " + computedGross + ", so it records no sale. Goods given "
                            + "away are a write-off with a reason on it, not an invoice for nothing.");
        }

        Rounding rounding = compareAgainstDocument(request, computedGross, currency);
        Money receivable = computedGross.plus(rounding.amount());

        PaymentMethodView paymentMethod = requireActivePaymentMethod(request.paymentMethodId());
        requireWithinCashLimit(paymentMethod, receivable);

        return new Computation(customer, series, paymentMethod, priced, computedGross, rounding,
                receivable, currency);
    }

    /**
     * ⚠️ <strong>A deactivated payment method may not settle a NEW invoice</strong> (R2b §4).
     *
     * <h2>Why this is here and not deferred</h2>
     *
     * <p>{@code payment_method.active} would be decoration without a rule that reads it, and there
     * is exactly one place a caller <em>chooses</em> a settlement method:
     * {@code NewSalesInvoice.paymentMethodId}, which arrives here. A credit note takes its method
     * from the invoice it refunds rather than from a caller — that is <em>holding</em>, not
     * <em>setting</em>, so it is deliberately not guarded.
     *
     * <p>It sits in {@code compute(...)}, which {@code record} and {@code preview} share, so an
     * entry screen learns before the operator submits — the same placement as R1b's channel-less,
     * inactive-series and inactive-type refusals.
     *
     * <h2>⚠️ SETTING IS REFUSED; HOLDING IS NOT</h2>
     *
     * <p>Deactivating a method does <strong>not</strong> touch invoices already settled by it: they
     * keep pointing at it and stay explicable, and reversing or crediting one still works. If that
     * were not so, deactivation would be destructive and the owner could never retire the methods he
     * does not use — which is the whole capability {@code active} exists for.
     */
    private PaymentMethodView requireActivePaymentMethod(long paymentMethodId) {
        PaymentMethodView paymentMethod = paymentMethods.require(paymentMethodId);
        if (!paymentMethod.active()) {
            throw new InvalidSalesInvoiceException(
                    "Payment method '" + paymentMethod.description() + "' is inactive, so it is "
                            + "not for new documents. Invoices already settled by it are "
                            + "unaffected. Choose another method, or reactivate it in Settings.");
        }
        return paymentMethod;
    }

    /**
     * The series a document is recorded in, and the three things R1b makes it decide.
     *
     * <p><strong>Everything about a sale that used to be typed in beside it now comes from here.</strong>
     * The channel is the series' own, so ΑΛΠW being the web series makes an invoice in it a web sale
     * by definition; the document type is reached through the series rather than named separately,
     * so the two cannot disagree about what kind of document this is.
     *
     * <p>All four refusals live here rather than at the call sites, because {@code compute} is what
     * {@code record} and {@code preview} share — so an entry screen learns that a series is
     * unusable <em>before</em> the operator submits, which is most of what a preview is for.
     */
    private SeriesContext resolveSeries(long seriesId) {
        SalesDocumentSeriesView series = salesSeries.require(seriesId);
        if (!series.active()) {
            throw new InvalidSalesInvoiceException(
                    "Sales document series '" + series.abbreviation() + "' is inactive, so nothing "
                            + "new should be recorded in it. A series is deactivated precisely so "
                            + "that stops; an inactive series stays readable so the documents "
                            + "already numbered in it remain explicable.");
        }

        // ⚠️ THE DRAFT HALF OF THIS REFUSAL IS UNREACHABLE BY CONSTRUCTION, and is written anyway —
        // the same standing as the affectsStock == null branch below, and recorded here so a future
        // reader does not spend a session trying to produce it (F5 A.4, measured 2026-08-05).
        //
        // A series can never come to point at a draft type. R2b guards BOTH places a type is set:
        // SalesDocumentSeriesServiceImpl refuses `create` and refuses the document-type change, in
        // each case naming the undecided stock behaviour. And a decided type cannot be pushed back
        // to draft — PUT /api/sales-document-types/{id}/stock-behaviour requires both flags and
        // answers 400 to a null one. So `documentType.isDraft()` is false at this point on every
        // reachable path, and the milder wording below is a backstop rather than a message anybody
        // will read.
        //
        // ⚠️ It stays for the reason the branch below stays: if either of R2b's guards is ever
        // relaxed, this fails loudly with the right explanation instead of recording a document
        // whose stock behaviour nobody decided. What must NOT happen is somebody "simplifying" the
        // two messages into one on the grounds that only the retired case is observed — a draft's
        // stock question being unanswered and a type being retired are genuinely different failures.
        SalesDocumentTypeView documentType = salesDocumentTypes.require(series.documentTypeId());
        if (!documentType.active()) {
            throw new InvalidSalesInvoiceException(
                    "Sales document series '" + series.abbreviation() + "' is of type '"
                            + documentType.description() + "', which is inactive. "
                            + (documentType.isDraft()
                                    ? "It is a draft — its stock behaviour has not been decided yet, "
                                            + "so recording against it could not say whether stock "
                                            + "moves."
                                    : "A retired type describes documents already issued under it, "
                                            + "so a new one cannot use it."));
        }

        // ⚠️ THE CHANNEL-LESS REFUSAL. sales_invoice.channel is NOT NULL and is deliberately NOT
        // relaxed to accommodate this: the constraint is what holds an open question open.
        if (series.channel() == null) {
            throw new InvalidSalesInvoiceException(
                    "Sales document series '" + series.abbreviation() + "' has no sales channel, so "
                            + "it is not a series a sale can be recorded in. The self-supply series "
                            + "(Στοιχείο Αυτοπαράδοσης / Ιδιοχρησιμοποίησης) are exactly this: the "
                            + "customer is the issuer, and there is no channel to attribute revenue "
                            + "to. Novocore cannot record one yet — self-supply has no posting rule, "
                            + "the revenue leg has no candidate account, and which accounts carry "
                            + "each leg is an accountant's question rather than one to guess at. "
                            + "This is refused rather than stored against a made-up channel, because "
                            + "a refusal keeps the question visible. Roadmap step R3 answers it.");
        }

        // ⚠️ Unreachable today, and written anyway. The table CHECK
        // sales_document_type_active_has_stock_behaviour means an ACTIVE type always has both stock
        // flags decided, and an inactive one has already been refused above. But null here means
        // NOBODY HAS DECIDED, and reading it as false would silently record "no stock moved" for a
        // question nobody answered — the exact confusion the nullable column exists to prevent
        // (A.6: "a false is a guess wearing a value's clothes"). If that CHECK is ever relaxed,
        // this fails loudly instead of quietly skipping the consumption.
        if (documentType.affectsStock() == null) {
            throw new InvalidSalesInvoiceException(
                    "Sales document type '" + documentType.description() + "' does not say whether "
                            + "documents of this type move stock, so recording one could not decide "
                            + "whether to consume any. Decide its stock behaviour first.");
        }

        return new SeriesContext(series, documentType, series.channel());
    }

    @Override
    @Transactional(readOnly = true)
    public SalesInvoicePreview preview(NewSalesInvoice request) {
        Computation computation = compute(request);

        List<SalesInvoicePreviewLine> lines = new ArrayList<>();
        Money net = Money.zero(computation.currency());
        Money vat = Money.zero(computation.currency());
        for (PricedLine line : computation.priced()) {
            lines.add(line.toPreviewLine());
            net = net.plus(line.net());
            vat = vat.plus(line.vat());
        }

        return new SalesInvoicePreview(
                lines, net, vat, computation.computedGross(),
                request.statedTotal(),
                computation.rounding().amount(),
                computation.rounding().threshold(),
                computation.rounding().needsAcceptance(),
                computation.receivable());
    }

    @Override
    @Transactional
    public SalesInvoiceView record(NewSalesInvoice request) {
        Computation computation = compute(request);
        CustomerView customer = computation.customer();
        List<PricedLine> priced = computation.priced();
        Rounding rounding = computation.rounding();
        Money receivable = computation.receivable();
        Currency currency = computation.currency();

        // The one refusal compute() reports rather than raises, because preview has to be able to
        // show the difference and offer the acceptance. Posting is where it becomes a refusal.
        if (rounding.needsAcceptance()) {
            throw new InvalidSalesInvoiceException(
                    "This invoice's lines come to " + computation.computedGross()
                            + " and the document states " + request.statedTotal()
                            + ", a difference of " + rounding.amount() + ". That is more than the "
                            + "rounding threshold of " + rounding.threshold() + ", so it is not a "
                            + "rounding residual — it is a disagreement somebody has to look at. Fix "
                            + "the lines, or record who accepts the difference and why; it will post "
                            + "to Rounding differences either way, because Accounts receivable has "
                            + "to agree with the document the customer holds.");
        }

        long entryId = post(request, computation.paymentMethod(), customer, priced, rounding,
                receivable, currency).id();

        SalesInvoice invoice = new SalesInvoice(customer.id(), computation.series().channel(),
                request.seriesId(),
                request.paymentMethodId(), request.documentNumber(), request.invoiceDate(),
                request.description(), request.statedTotal(), rounding.amount(),
                rounding.neededReview(), rounding.acceptedBy(), rounding.acceptedAt(),
                request.roundingNote(), null);
        invoice.postedAs(entryId);
        for (PricedLine line : priced) {
            SalesInvoiceLine entity = invoice.addLine(line.toEntity());
            for (BundleComponentLine component : line.componentLines()) {
                entity.addComponent(component.componentProductId(), component.quantity(),
                        component.allocatedAmount());
            }
        }
        SalesInvoice saved = invoices.save(invoice);
        // Flushed so the lines have ids: a serialized unit records the line that sold it, and a unit
        // pointing at a line that does not exist yet is the problem a match created before its line is.
        invoices.flush();

        // ⚠️ THE STOCK BRANCH, AND IT IS SILENT BY DECISION.
        //
        // ΑΛΠ and ΤΠΔΑ combine sale and transport, so stock moves. A plain Τιμολόγιο is purely
        // sales and does not reduce it — the goods leave on a separate dispatch document (18b),
        // and this business issues both routinely. A non-moving type therefore creates NO
        // stock_consumption row at all: not a pending one, not a placeholder, not a row with a
        // marker on it. stock_consumption's source CHECK is deliberately NOT widened, because
        // there is nothing new to record.
        //
        // There is also no state, no flag and no warning anywhere else. That is the owner's
        // decision, taken as a decision: an indicator nobody acts on is a second thing to keep
        // true. ⚠️ The consequence is real and is recorded rather than mitigated — until 18b
        // exists, stock figures are incomplete for every non-stock-moving sales document, which
        // is a routine share of real sales.
        if (computation.series().affectsStock()) {
            consumeStock(saved, priced, customer, request.invoiceDate());
        }

        auditLog.record("sales-invoice.recorded", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "customer", customer.name(),
                "number", saved.getDocumentNumber(),
                "series", computation.series().series().abbreviation(),
                "channel", saved.getChannel().name(),
                "paymentMethod", computation.paymentMethod().description(),
                "invoiceDate", saved.getInvoiceDate().toString(),
                "gross", receivable.toString(),
                "rounding", rounding.amount().toString(),
                "lines", String.valueOf(priced.size()),
                "journalEntry", String.valueOf(entryId)));

        return toView(saved);
    }

    @Override
    @Transactional
    public SalesInvoiceView reverse(long invoiceId, LocalDate reversalDate, String reason) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        SalesInvoice original = load(invoiceId);

        if (original.isReversal()) {
            throw new InvalidSalesInvoiceException(
                    "Sales invoice " + invoiceId + " is itself the reversal of invoice "
                            + original.getReversalOfId() + ". Reversing it would re-create the sale "
                            + "while claiming to be a correction; record the invoice again instead.");
        }
        invoices.findByReversalOfId(invoiceId).ifPresent(existing -> {
            throw new InvalidSalesInvoiceException(
                    "Sales invoice " + invoiceId + " has already been reversed by document "
                            + existing.getId() + ". Reversing it twice would credit the customer "
                            + "twice, with both halves looking correct.");
        });
        if (!creditNotes.findBySalesInvoiceIdOrderByIdAsc(invoiceId).isEmpty()) {
            throw new InvalidSalesInvoiceException(
                    "Sales invoice " + invoiceId + " has credit notes against it, so it is a sale "
                            + "that happened and was partly returned rather than one recorded in "
                            + "error. Reverse the credit notes first if the whole document is wrong.");
        }

        long reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                original.getJournalEntryId(),
                reversalDate,
                "Reversal of sales invoice " + invoiceId + " (" + original.getDocumentNumber() + ")"
                        + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()),
                JournalSource.SALES_INVOICE,
                journal.mirrorOf(original.getJournalEntryId()))).id();

        // The stock has to come back with the money, for JournalSource's reason: reversing only the
        // entry would leave goods off the shelf that the balance sheet no longer carries, and a
        // machine sold to somebody on a document that no longer exists.
        for (SalesInvoiceLine line : original.getLines()) {
            if (line.getStockConsumptionId() != null) {
                inventory.reverseConsumption(line.getStockConsumptionId(), reversalDate,
                        "Sales invoice " + invoiceId + " reversed");
            }
            for (SalesInvoiceLineComponent component : line.getComponents()) {
                if (component.getStockConsumptionId() != null) {
                    inventory.reverseConsumption(component.getStockConsumptionId(), reversalDate,
                            "Sales invoice " + invoiceId + " reversed");
                }
            }
        }

        // No lines of its own: everything a reader needs is on the original, which stays exactly as
        // posted, and duplicating them would give one sale two statements of itself.
        // The series is carried for the same reason the channel is: a reversal that showed no
        // series while its original had one would read as a document from before series existed.
        SalesInvoice reversal = new SalesInvoice(original.getCustomerId(), original.getChannel(),
                original.getSeriesId(),
                original.getPaymentMethodId(), original.getDocumentNumber(), reversalDate,
                reason == null || reason.isBlank() ? null : reason.trim(), null,
                Money.zero(original.getRoundingAmount().currency()), false, null, null, null,
                invoiceId);
        reversal.postedAs(reversingEntryId);
        SalesInvoice saved = invoices.save(reversal);

        auditLog.record("sales-invoice.reversed", ENTITY_TYPE, String.valueOf(invoiceId), Map.of(
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
    public Optional<SalesInvoiceView> find(long invoiceId) {
        return invoices.findById(invoiceId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesInvoiceView require(long invoiceId) {
        return toView(load(invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceView> ofCustomer(long customerId) {
        return invoices.findByCustomerIdOrderByInvoiceDateAscIdAsc(customerId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceView> between(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        return invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(from, to).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * The logical sort names this list accepts, and the entity property each orders by.
     *
     * <p>Stated here rather than derived from {@link SalesInvoiceSort}'s constant names, so adding a
     * value to that enum does not silently start ordering by a property that happens to share its
     * name — or fail at runtime because none does. The mapping is a decision; the enum is the
     * vocabulary.
     *
     * <h2>⚠️ This map is where a Greek-aware text ordering CANNOT be expressed (F5 B.4, 2026-08-05)</h2>
     *
     * <p>Every entry here becomes a property name on a Spring Data {@code Sort}, built by
     * {@code SpringPaging.pageableFor} and handed to {@code findAll}. <strong>A {@code Sort} cannot
     * carry a {@code COLLATE} clause.</strong> It names a property; the collation comes from the
     * column, and under this deployment's {@code --locale=C} that means <strong>byte order</strong> —
     * every Greek document number after every Latin one, and mixed case split.
     *
     * <p><strong>So the day a text sort key has to order Greek correctly, the change is here and it
     * is a change of MECHANISM, not of expression.</strong> That property leaves the
     * {@code Pageable}-driven path entirely — an explicit {@code @Query} with
     * {@code ORDER BY … COLLATE "el-GR-x-icu"} and its own paging — while the others stay on it. This
     * note exists so that is one ordering path being replaced rather than a rewrite discovered
     * halfway through.
     *
     * <p><strong>Why it is a note and not a build.</strong> The only shipping text key is
     * {@code DOCUMENT_NUMBER}, and the two orders were measured against the live stack on 2026-08-05:
     * on <em>Latin</em> document numbers, byte order and {@code el-GR-x-icu} <strong>agree</strong>.
     * They diverge only where the text carries Greek letters or mixed case. ⚠️ <strong>The deferral is
     * therefore conditional on a fact nobody here owns: whether a real Prosvasis Go document number
     * carries Greek letters.</strong> If it does, this stops being deferred for
     * {@code DOCUMENT_NUMBER} immediately. The frontend's half of the same obligation — why
     * {@code CUSTOMER_NAME} is not offered at all — is recorded at
     * {@code sales-invoice-columns.tsx}.
     *
     * <p>📌 Numeric ordering is off under <em>both</em> orders ({@code -0010} sorts before
     * {@code -0002}), deliberately and consistently with {@code collation.test.ts}. Collation is not
     * what would fix that.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            SalesInvoiceSort.INVOICE_DATE.name(), "invoiceDate",
            SalesInvoiceSort.DOCUMENT_NUMBER.name(), "documentNumber",
            SalesInvoiceSort.RECORDED_AT.name(), "createdAt");

    /** Ordering when the caller names none: the document's own date, as the unpaged list does. */
    private static final String NATURAL_ORDER = "invoiceDate";

    /**
     * Target-list row 8, as a specification — <strong>document number, plus the counterparty and the
     * series, which are not on this table</strong> (F5 B.1, 2026-08-05).
     *
     * <p>The two related halves go through {@link TextSearch#matchingRelated}, which reaches them by
     * subquery on the scalar id rather than by a mapped association. The whole argument for that —
     * and why the dotted path this mechanism advertises cannot serve a document list — is written at
     * that method. The short version: {@code customerId} and {@code seriesId} are plain
     * {@code Long}s, and an inner join would drop every invoice whose series is null.
     *
     * <p>⚠️ <strong>Customer <em>code</em> is in row 8 and is missing here, deliberately.</strong>
     * {@code customer.code} is not a column — it is D1's to add, and D1 is scheduled after F5. When
     * it lands, it is one more property name in the call below.
     *
     * <p>A null or blank term makes every part always-true, so the composed predicate is "no filter"
     * rather than "match nothing" — the semantics {@code TextSearch} already documents.
     */
    private static Specification<SalesInvoice> matching(String search) {
        return TextSearch.<SalesInvoice>matching(search, "documentNumber")
                .or(CustomerSearch.matchingCustomer(search, "customerId"))
                .or(SalesDocumentSeriesSearch.matchingSeries(search, "seriesId"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesInvoiceView> pageBetween(
            LocalDate from, LocalDate to, String search, PageRequest page) {
        requireOrderedRange(from, to);
        Specification<SalesInvoice> withinRange = (root, query, builder) ->
                builder.between(root.get("invoiceDate"), from, to);
        return SpringPaging.responseFrom(
                invoices.findAll(withinRange.and(matching(search)),
                        SpringPaging.pageableFor(page, SORTABLE, NATURAL_ORDER)),
                this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesInvoiceView> pageOfCustomer(
            long customerId, String search, PageRequest page) {
        Specification<SalesInvoice> ofCustomer = (root, query, builder) ->
                builder.equal(root.get("customerId"), customerId);
        return SpringPaging.responseFrom(
                invoices.findAll(ofCustomer.and(matching(search)),
                        SpringPaging.pageableFor(page, SORTABLE, NATURAL_ORDER)),
                this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesInvoiceView> findByJournalEntry(long journalEntryId) {
        return invoices.findByJournalEntryId(journalEntryId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceView> withAcceptedRoundingDifference(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        return invoices.findWithAcceptedRoundingDifference(from, to).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money totalRoundingBetween(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        Money total = Money.zero(Money.EUR);
        for (SalesInvoice invoice : invoices.findWithRoundingBetween(from, to)) {
            total = total.plus(invoice.getRoundingAmount());
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------
    // Pricing one line — where the precedence rule actually happens
    // ---------------------------------------------------------------------------------------

    private PricedLine price(NewSalesInvoiceLine line, CustomerView customer, SalesChannel channel,
            Currency currency, RoundingMode roundingMode) {
        ProductView product = null;
        ChargeTypeView chargeType = null;
        Long productDefaultVatClassId = null;

        if (line.isProduct()) {
            product = products.require(line.productId());
            if (!product.active()) {
                throw new InvalidSalesInvoiceException(
                        "Product '" + product.sku() + "' is inactive, so nothing new should be sold "
                                + "against it.");
            }
            productDefaultVatClassId = product.defaultVatClassId();
        } else {
            chargeType = chargeTypes.require(line.chargeTypeId());
            if (!chargeType.active()) {
                throw new InvalidSalesInvoiceException(
                        "Charge type '" + chargeType.name() + "' is inactive, so it cannot be charged "
                                + "on a new invoice.");
            }
            productDefaultVatClassId = chargeType.defaultVatClassId();
        }

        VatClassView vatClass = null;
        VatClassSource vatSource = null;
        if (line.isExempt()) {
            // An exempt line has no rate to resolve, so the precedence rule does not run at all —
            // stating an article is a statement that the supply is outside the scope, which no
            // customer-level or product-level rate may override.
            exemptionReasons.require(line.vatExemptionReasonId());
        } else {
            // Invoice line beats customer beats product (VatClassPrecedence), with the charge type
            // standing in for the product on a fee line. Q33 says a fee's rate does not follow the
            // goods on the invoice, and this respects that: what it takes is the fee's OWN default,
            // never a rate derived from the lines around it.
            VatClassResolution resolution = VatClassPrecedence.resolve(
                    line.vatClassId(), customer.vatClassOverrideId(), productDefaultVatClassId);
            vatClass = vatClasses.require(resolution.vatClassId());
            vatSource = resolution.source();
            if (!vatClass.active()) {
                throw new InvalidSalesInvoiceException(
                        "VAT class '" + vatClass.code() + "' is inactive. A retired class describes "
                                + "invoices already issued under it, so a new one cannot use it.");
            }
        }

        Money net = Money.rounded(line.netExactly(), currency, roundingMode);
        Money vat = vatClass == null
                ? Money.zero(currency)
                : Money.rounded(
                        net.amount().multiply(vatClass.ratePercent().percent()).divide(ONE_HUNDRED),
                        currency, roundingMode);

        if (line.isProduct()) {
            AccountSystemKey revenueAccount = product.type() == ProductType.SERVICE
                    ? AccountSystemKey.SERVICES_INCOME
                    : channel.revenueAccount();

            List<BundleComponentLine> componentLines = List.of();
            if (product.bundle()) {
                // Decomposed against the line's NET, so the two revenue levels are the same money.
                // ProportionalAllocation is exact integer arithmetic in cents, so this produces no residual
                // of its own — the only rounding on this invoice is the comparison with the document.
                BundleDecomposition decomposition =
                        bundles.decompose(product.id(), line.quantity(), net);
                componentLines = decomposition.componentLines();
            }
            if (line.isSerialized() && !product.serialTracked()) {
                throw new InvalidSalesInvoiceException(
                        "Line for '" + product.sku() + "' names serial numbers, but the product is "
                                + "not serial-tracked. Serial numbers would name units that do not "
                                + "exist.");
            }
            if (!line.isSerialized() && product.serialTracked()) {
                throw new InvalidSalesInvoiceException(
                        "Product '" + product.sku() + "' is serial-tracked, so the line has to name "
                                + "the machines that left the shelf — brief §5 puts the customer and "
                                + "invoice on each unit, and costs it at its own actual cost.");
            }
            return PricedLine.product(line, product, revenueAccount, net, vat,
                    vatClass == null ? null : vatClass.id(), vatSource, componentLines);
        }

        AccountView incomeAccount = chartOfAccounts.requireAccount(chargeType.incomeAccountId());
        if (!incomeAccount.active()) {
            throw new InvalidSalesInvoiceException(
                    "Charge type '" + chargeType.name() + "' credits '" + incomeAccount.name()
                            + "', which is inactive.");
        }
        return PricedLine.charge(line, chargeType, incomeAccount.id(), net, vat,
                vatClass == null ? null : vatClass.id(), vatSource);
    }

    /**
     * Brief §6's rounding comparison, per document (Q15).
     *
     * <p>The difference is <strong>always posted</strong>, so that Accounts receivable agrees with the
     * document the customer holds. The threshold decides only whether somebody had to agree to it
     * first: at or below it the difference posts automatically, above it the invoice is refused until
     * {@code roundingAcceptedBy} names who accepted it. That is rule 7's "auto-resolve what is
     * certain, require one-click confirmation for everything else", applied where the person who can
     * explain the difference is standing.
     */
    private Rounding compareAgainstDocument(
            NewSalesInvoice request, Money computedGross, Currency currency) {
        Money threshold = settings.requireEurAmount(SettingKeys.LEDGER_ROUNDING_THRESHOLD);

        Money stated = request.statedTotal();
        if (stated == null) {
            return Rounding.none(currency, threshold);
        }
        if (!stated.currency().equals(currency)) {
            throw new InvalidSalesInvoiceException(
                    "This invoice is priced in " + currency.getCurrencyCode() + " and its stated "
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
            // Reported, not thrown: an entry screen has to be able to show this difference and
            // offer the acceptance before the operator submits, and it cannot do that if asking
            // what the difference is refuses to answer. record() raises it; see compute().
            return Rounding.unaccepted(difference, threshold);
        }
        return Rounding.accepted(difference, request.roundingAcceptedBy(), threshold);
    }

    /**
     * Brief §6's Greek legal cash limit, blocked rather than flagged.
     *
     * <p>The one place in this design where a check refuses instead of asking for confirmation, and
     * deliberately: the confirmation nobody can give is the legality of the transaction. Penalties
     * under N. 5301/2026 reach double the cash amount.
     */
    private void requireWithinCashLimit(PaymentMethodView method, Money amount) {
        if (!method.subjectToCashLimit()) {
            return;
        }
        Money limit = settings.requireEurAmount(SettingKeys.CASH_PAYMENT_LIMIT);
        if (amount.compareTo(limit) >= 0) {
            throw new InvalidSalesInvoiceException(
                    "A cash sale of " + amount + " reaches the legal cash limit of " + limit
                            + " and is blocked. Under N. 5301/2026 the penalty runs to double the "
                            + "cash amount, so this is refused rather than flagged — take it by card "
                            + "or bank transfer.");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    /**
     * Debit the settlement account or Accounts receivable with what the customer owes, credit revenue
     * per account, credit Output VAT per class, and put any rounding difference where it belongs.
     */
    private JournalEntryView post(NewSalesInvoice request, PaymentMethodView paymentMethod,
            CustomerView customer,
            List<PricedLine> priced, Rounding rounding, Money receivable, Currency currency) {
        AccountView outputVat = chartOfAccounts.requireAccount(AccountSystemKey.OUTPUT_VAT);

        List<NewJournalLine> lines = new ArrayList<>();

        // The debit side. A method that settles immediately debits its own account and the invoice is
        // born fully settled; the rest debit AR and the invoice is an open item until a Receipt
        // allocates against it (brief §6).
        // ⚠️ SINCE R4 THE ACCOUNT IS READ OFF THE METHOD IN EVERY CASE. It used to be resolved from
        // an AccountSystemKey baked into an enum constant, with a null meaning "debit Accounts
        // receivable" BY CONVENTION. Now Επί πιστώσει NAMES Accounts receivable, so no null means
        // anything and there is nothing to interpret.
        //
        // The one remaining branch is not about which account — it is about whether the line must
        // carry a sub-ledger reference, which a CONTROL account requires and a bank, cash or
        // partner-clearing account forbids.
        if (paymentMethod.settlesImmediately()) {
            lines.add(NewJournalLine.debit(paymentMethod.accountId(), receivable)
                    .describedAs(paymentMethod.description() + " — " + customer.name()));
        } else {
            lines.add(NewJournalLine.debit(paymentMethod.accountId(), receivable)
                    .forSubLedger(SubLedgerRef.customer(customer.id())));
        }

        // Revenue, one line per account rather than one per invoice line: several products through one
        // channel are one credit, and which products they were is on the document.
        Map<Long, Money> revenueByAccount = new LinkedHashMap<>();
        Map<Long, Money> vatByClass = new LinkedHashMap<>();
        Map<Long, Money> baseByClass = new LinkedHashMap<>();

        for (PricedLine line : priced) {
            revenueByAccount.merge(line.revenueAccountId(this), line.net(), Money::plus);
            if (line.vat().isPositive()) {
                vatByClass.merge(line.vatClassId(), line.vat(), Money::plus);
                baseByClass.merge(line.vatClassId(), line.net(), Money::plus);
            }
        }

        revenueByAccount.forEach(
                (accountId, amount) -> lines.add(NewJournalLine.credit(accountId, amount)));

        // Q14's dimension, supplied here — the step 7 obligation. Without the class and the base on
        // the line, two Output VAT lines at different rates are two indistinguishable amounts against
        // one account, and a VAT return assembled from the ledger would silently understate.
        vatByClass.forEach((classId, amount) -> lines.add(
                NewJournalLine.credit(outputVat.id(), amount)
                        .withVat(VatDimension.of(classId, baseByClass.get(classId)))));

        if (!rounding.amount().isZero()) {
            AccountView roundingAccount =
                    chartOfAccounts.requireAccount(AccountSystemKey.ROUNDING_DIFFERENCES);
            lines.add(rounding.amount().isPositive()
                    ? NewJournalLine.credit(roundingAccount.id(), rounding.amount())
                            .describedAs("Rounding against document total")
                    : NewJournalLine.debit(roundingAccount.id(), rounding.amount().abs())
                            .describedAs("Rounding against document total"));
        }

        String description = request.description() != null
                ? request.description()
                : "Sales invoice " + request.documentNumber() + " — " + customer.name();

        return journal.post(NewJournalEntry.of(
                request.invoiceDate(), description, JournalSource.SALES_INVOICE, lines));
    }

    // ---------------------------------------------------------------------------------------
    // Taking the stock out — the second entry
    // ---------------------------------------------------------------------------------------

    private void consumeStock(SalesInvoice invoice, List<PricedLine> priced, CustomerView customer,
            LocalDate invoiceDate) {
        List<SalesInvoiceLine> savedLines = invoice.getLines();
        for (int i = 0; i < priced.size(); i++) {
            PricedLine line = priced.get(i);
            SalesInvoiceLine entity = savedLines.get(i);
            if (!line.isProduct()) {
                continue;
            }

            if (!line.componentLines().isEmpty()) {
                consumeBundleComponents(entity, line, customer, invoiceDate);
                continue;
            }
            if (!line.product().isStocked()) {
                // A service takes nothing off a shelf. Nothing is posted for its cost either: brief §5
                // has no cost for a service, and inventing one would be a guess about labour.
                continue;
            }

            NewStockConsumption request = line.request().isSerialized()
                    ? NewStockConsumption.ofUnits(line.product().id(), line.request().serialNumbers(),
                            invoiceDate, JournalSource.SALES_INVOICE,
                            SaleReference.of(customer.id(), entity.getId()))
                    : NewStockConsumption.of(line.product().id(), line.request().quantity(),
                            invoiceDate, JournalSource.SALES_INVOICE);
            StockConsumptionView consumption = inventory.consume(
                    request.noted("Sales invoice " + invoice.getDocumentNumber()));
            entity.consumedAs(consumption.id());
        }
    }

    /**
     * A bundle takes its stock at component level, because the bundle itself has none (brief §5).
     *
     * <p>Only <em>stocked</em> components consume: a machine sold with its installation is a real
     * bundle, and the installation takes allocated revenue and nothing off a shelf.
     */
    private void consumeBundleComponents(SalesInvoiceLine entity, PricedLine line,
            CustomerView customer, LocalDate invoiceDate) {
        List<SalesInvoiceLineComponent> savedComponents = entity.getComponents();
        List<BundleComponentLine> componentLines = line.componentLines();

        for (int i = 0; i < componentLines.size(); i++) {
            BundleComponentLine component = componentLines.get(i);
            if (!component.stocked()) {
                continue;
            }
            ProductView componentProduct = products.require(component.componentProductId());
            if (componentProduct.serialTracked()) {
                throw new InvalidSalesInvoiceException(
                        "Bundle '" + line.product().sku() + "' contains the serial-tracked product '"
                                + componentProduct.sku() + "', and a bundle line names no serial "
                                + "numbers. Which machine left the shelf is a fact somebody scanned, "
                                + "not something a bundle definition may decide — sell the machine on "
                                + "its own line.");
            }
            StockConsumptionView consumption = inventory.consume(
                    NewStockConsumption.of(component.componentProductId(), component.quantity(),
                                    invoiceDate, JournalSource.SALES_INVOICE)
                            .noted("Bundle '" + line.product().sku() + "' on invoice "
                                    + entity.getInvoice().getDocumentNumber()));
            savedComponents.get(i).consumedAs(consumption.id());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------

    private SalesInvoice load(long invoiceId) {
        return invoices.findById(invoiceId)
                .orElseThrow(() -> new SalesInvoiceNotFoundException(invoiceId));
    }

    /** The series' abbreviation for display, or null where the invoice predates series (R1b). */
    private String seriesAbbreviationOf(Long seriesId) {
        return seriesId == null
                ? null
                : salesSeries.find(seriesId)
                        .map(SalesDocumentSeriesView::abbreviation)
                        .orElse(null);
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

    private SalesInvoiceView toView(SalesInvoice invoice) {
        CustomerView customer = customers.require(invoice.getCustomerId());
        // ⚠️ Nullable in the schema and required by the service (V37, on V33's precedent for
        // series_id), so a pre-R4 row can genuinely have none. Rendered as such rather than
        // fabricating a method nobody chose.
        PaymentMethodView paymentMethod = invoice.getPaymentMethodId() == null
                ? null : paymentMethods.require(invoice.getPaymentMethodId());
        List<SalesInvoiceLineView> lineViews =
                invoice.getLines().stream().map(this::toView).toList();

        Currency currency = invoice.getRoundingAmount().currency();
        Money netTotal = Money.zero(currency);
        Money vatTotal = Money.zero(currency);
        for (SalesInvoiceLineView line : lineViews) {
            netTotal = netTotal.plus(line.netAmount());
            vatTotal = vatTotal.plus(line.vatAmount());
        }

        return new SalesInvoiceView(
                invoice.getId(),
                customer.id(),
                customer.name(),
                invoice.getChannel(),
                paymentMethod == null ? 0L : paymentMethod.id(),
                paymentMethod == null ? "(not recorded)" : paymentMethod.description(),
                paymentMethod != null && paymentMethod.settlesImmediately(),
                invoice.getDocumentNumber(),
                invoice.getInvoiceDate(),
                invoice.getDescription(),
                netTotal,
                vatTotal,
                netTotal.plus(vatTotal).plus(invoice.getRoundingAmount()),
                invoice.getStatedTotal(),
                invoice.getRoundingAmount(),
                invoice.isRoundingNeededReview(),
                invoice.getRoundingAcceptedBy(),
                invoice.getRoundingAcceptedAt(),
                invoice.getRoundingNote(),
                invoice.getJournalEntryId(),
                invoice.getReversalOfId(),
                invoices.findByReversalOfId(invoice.getId()).map(SalesInvoice::getId).orElse(null),
                // The statutory identifiers, read straight off the row. Null on every invoice as
                // of R1a and that is correct: Novocore never obtains a ΜΑΡΚ itself, and nothing in
                // this phase is entitled to supply one.
                invoice.getMark(),
                invoice.getUid(),
                invoice.getQrUrl(),
                invoice.getTransmissionStatus(),
                invoice.getSeriesId(),
                // R1b resolves the abbreviation, which R1a left null because nothing had a series.
                // `find` rather than `require`: an invoice recorded before R1b has no series at
                // all, and a reading path must not throw on the ordinary history it exists to show.
                seriesAbbreviationOf(invoice.getSeriesId()),
                lineViews);
    }

    private SalesInvoiceLineView toView(SalesInvoiceLine line) {
        List<SalesInvoiceLineComponentView> componentViews = line.getComponents().stream()
                .map(component -> new SalesInvoiceLineComponentView(
                        component.getId(),
                        component.getComponentNumber(),
                        component.getProductId(),
                        products.require(component.getProductId()).sku(),
                        component.getQuantity(),
                        component.getAllocatedAmount(),
                        component.getStockConsumptionId()))
                .toList();

        List<String> serials = List.of();
        if (line.getStockConsumptionId() != null && line.getProductId() != null
                && products.require(line.getProductId()).serialTracked()) {
            serials = inventory.unitsOfProduct(line.getProductId()).stream()
                    .filter(unit -> Objects.equals(unit.soldOnInvoiceLineId(), line.getId()))
                    .map(unit -> unit.serialNumber())
                    .toList();
        }

        return new SalesInvoiceLineView(
                line.getId(),
                line.getLineNumber(),
                line.getLineType(),
                line.getProductId(),
                line.getProductId() == null ? null : products.require(line.getProductId()).sku(),
                line.getChargeTypeId(),
                line.getChargeTypeId() == null
                        ? null : chargeTypes.require(line.getChargeTypeId()).name(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getNetAmount(),
                line.getVatAmount(),
                line.getVatClassId(),
                line.getVatClassSource(),
                line.getVatExemptionReasonId(),
                line.getStockConsumptionId(),
                serials,
                line.getDescription(),
                componentViews);
    }

    // ---------------------------------------------------------------------------------------
    // Internals — one line's arithmetic, carried from validation through to posting
    // ---------------------------------------------------------------------------------------

    /**
     * The result of comparing our arithmetic against the source document.
     *
     * @param neededReview whether the difference exceeded the threshold and therefore had to be
     *     accepted explicitly. Frozen here so a later change to the threshold cannot retroactively
     *     alter which invoices somebody had to agree to.
     */
    /**
     * The outcome of comparing the lines against the document, and the threshold it was compared to.
     *
     * <p>{@code threshold} is carried rather than looked up again by whoever needs it, so the figure
     * a preview shows an operator is <em>the one the comparison actually used</em> and not a second
     * read of a setting that could have changed in between.
     *
     * @param needsAcceptance the difference exceeds the threshold and nobody has accepted it. Not a
     *     refusal in itself — see {@link #compute} for why this is reported rather than thrown.
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
     * The series a sale is recorded in, its document type, and the channel that follows from them.
     *
     * <p>Resolved once in {@link #resolveSeries} and carried, rather than looked up again where each
     * is needed. The channel is a component of its own even though it is {@code series.channel()},
     * because {@link #resolveSeries} has already refused a null one — so a reader here does not have
     * to re-establish that it cannot be null, and no caller is tempted to handle a case that cannot
     * arrive.
     *
     * @param documentType what decides whether recording this document consumes stock. ⚠️
     *     {@code affectsStock} is non-null by the time this exists; {@link #resolveSeries} refuses
     *     otherwise, because null means <em>nobody has decided</em> and never <em>false</em>.
     */
    private record SeriesContext(
            SalesDocumentSeriesView series,
            SalesDocumentTypeView documentType,
            SalesChannel channel) {

        boolean affectsStock() {
            return Boolean.TRUE.equals(documentType.affectsStock());
        }
    }

    /**
     * Everything worked out from a request, before anything is written.
     *
     * <p>The single value {@link #record} and {@link #preview} share, which is what makes "the
     * preview cannot drift from the posting" a property of the code rather than a promise.
     */
    private record Computation(
            CustomerView customer,
            SeriesContext series,
            /** Resolved once in compute(), so posting and the audit entry cannot disagree with the
             * guard that admitted it. */
            PaymentMethodView paymentMethod,
            List<PricedLine> priced,
            Money computedGross,
            Rounding rounding,
            Money receivable,
            Currency currency) {
    }

    /**
     * One request line with its figures worked out — computed once and carried, rather than recomputed
     * for validation and again for posting. The second computation is where the two would drift apart.
     */
    private record PricedLine(
            NewSalesInvoiceLine request,
            ProductView product,
            ChargeTypeView chargeType,
            AccountSystemKey revenueAccountKey,
            Long revenueAccountId,
            Money net,
            Money vat,
            Long vatClassId,
            VatClassSource vatClassSource,
            List<BundleComponentLine> componentLines) {

        static PricedLine product(NewSalesInvoiceLine request, ProductView product,
                AccountSystemKey revenueAccountKey, Money net, Money vat, Long vatClassId,
                VatClassSource vatClassSource, List<BundleComponentLine> componentLines) {
            return new PricedLine(request, product, null, revenueAccountKey, null, net, vat,
                    vatClassId, vatClassSource, componentLines);
        }

        static PricedLine charge(NewSalesInvoiceLine request, ChargeTypeView chargeType,
                long incomeAccountId, Money net, Money vat, Long vatClassId,
                VatClassSource vatClassSource) {
            return new PricedLine(request, null, chargeType, null, incomeAccountId, net, vat,
                    vatClassId, vatClassSource, List.of());
        }

        boolean isProduct() {
            return request.isProduct();
        }

        long revenueAccountId(SalesInvoiceServiceImpl service) {
            return revenueAccountId != null
                    ? revenueAccountId
                    : service.chartOfAccounts.requireAccount(revenueAccountKey).id();
        }

        Money gross() {
            return net.plus(vat);
        }

        /**
         * The same figures the entity would carry, for a caller who is not posting.
         *
         * <p>Built from this record rather than recomputed, which is the whole point: a preview line
         * and a posted line differ in what happens to them, never in what they say.
         */
        SalesInvoicePreviewLine toPreviewLine() {
            return new SalesInvoicePreviewLine(
                    isProduct() ? product.id() : null,
                    isProduct() ? null : chargeType.id(),
                    request.description(),
                    request.quantity(),
                    request.unitPrice(),
                    net, vat, gross(),
                    vatClassId, vatClassSource, request.vatExemptionReasonId());
        }

        SalesInvoiceLine toEntity() {
            Quantity quantity = request.quantity();
            UnitCost unitPrice = request.unitPrice();
            return isProduct()
                    ? SalesInvoiceLine.product(product.id(), quantity, unitPrice, net, vat,
                            vatClassId, vatClassSource, request.vatExemptionReasonId(),
                            request.description())
                    : SalesInvoiceLine.charge(chargeType.id(), quantity, unitPrice, net, vat,
                            vatClassId, vatClassSource, request.vatExemptionReasonId(),
                            request.description());
        }
    }
}
