package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptLineView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.InvalidGoodsReceiptException;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
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
 * Physical deliveries — ADR 0004's inventory event.
 *
 * <p>A receipt creates the lots <em>and</em> posts, in one transaction: debit Inventory once per lot,
 * credit GR/IR clearing against the supplier. {@code InventoryService.receive} is the lower layer it
 * calls and posts nothing by itself, because this is the document that knows which supplier the
 * clearing is against.
 *
 * <p>A line matched to an existing invoice line takes its unit cost from that invoice, which is why
 * that direction can never produce a variance. An unmatched line states a provisional cost and keeps
 * it, whatever a later invoice says (ADR 0008).
 */
@Service
class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private static final String ENTITY_TYPE = "GoodsReceipt";

    private final GoodsReceiptRepository receipts;
    private final GoodsReceiptLineRepository receiptLines;
    private final PurchaseInvoiceRepository invoices;
    private final PurchaseInvoiceLineRepository invoiceLines;
    private final GrIrMatchRepository matches;
    private final SupplierService suppliers;
    private final ProductService products;
    private final ChartOfAccountsService chartOfAccounts;
    private final InventoryService inventory;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    GoodsReceiptServiceImpl(GoodsReceiptRepository receipts, GoodsReceiptLineRepository receiptLines,
            PurchaseInvoiceRepository invoices, PurchaseInvoiceLineRepository invoiceLines,
            GrIrMatchRepository matches, SupplierService suppliers, ProductService products,
            ChartOfAccountsService chartOfAccounts, InventoryService inventory,
            JournalService journal, SettingsService settings, AuditLogService auditLog) {
        this.receipts = receipts;
        this.receiptLines = receiptLines;
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.matches = matches;
        this.suppliers = suppliers;
        this.products = products;
        this.chartOfAccounts = chartOfAccounts;
        this.inventory = inventory;
        this.journal = journal;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Receiving
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public GoodsReceiptView record(NewGoodsReceipt request) {
        Objects.requireNonNull(request, "request");
        SupplierView supplier = suppliers.require(request.supplierId());
        if (!supplier.active()) {
            throw new InvalidGoodsReceiptException(
                    "Supplier '" + supplier.name() + "' is inactive, so nothing new should be "
                            + "arriving from them.");
        }

        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);

        List<ArrivingLine> arriving = new ArrayList<>();
        for (NewGoodsReceiptLine line : request.lines()) {
            arriving.add(prepare(line, supplier));
        }

        GoodsReceipt receipt = new GoodsReceipt(supplier.id(), request.deliveryNoteNumber(),
                request.receiptDate(), request.description(), null);
        for (ArrivingLine line : arriving) {
            receipt.addLine(new GoodsReceiptLine(line.product().id(), line.quantity(),
                    line.unitCost(), line.request().location(), line.invoiceLineId()));
        }
        GoodsReceipt saved = receipts.save(receipt);
        // Flushed so the lines have ids: a lot points at the line it came from, and the foreign key
        // cannot be satisfied by a row that has not been written yet.
        receipts.flush();

        List<GoodsReceiptLine> savedLines = saved.getLines();
        Map<Long, Long> lotIdByLine = new LinkedHashMap<>();
        for (int i = 0; i < arriving.size(); i++) {
            ArrivingLine line = arriving.get(i);
            GoodsReceiptLine entity = savedLines.get(i);

            NewInventoryLot lot = (line.request().isSerialized()
                    ? NewInventoryLot.serialized(line.product().id(), line.unitCost(),
                            request.receiptDate(), line.request().location(),
                            line.request().serialNumbers())
                    : NewInventoryLot.pooled(line.product().id(), line.quantity(), line.unitCost(),
                            request.receiptDate(), line.request().location()))
                    .receivedOn(entity.getId());
            if (line.request().roastDate() != null) {
                lot = lot.roastedOn(line.request().roastDate());
            }
            lotIdByLine.put(entity.getId(), inventory.receive(lot).id());

            if (line.invoiceLineId() != null) {
                PurchaseInvoiceLine invoiceLine = invoiceLines.findById(line.invoiceLineId())
                        .orElseThrow(() -> PurchaseInvoiceNotFoundException.forLine(
                                line.invoiceLineId()));
                matches.save(new GrIrMatch(invoiceLine, entity, line.quantity()));
            }
        }

        saved.postedAs(post(saved, supplier, arriving, savedLines, lotIdByLine, roundingMode));

        auditLog.record("goods-receipt.recorded", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "supplier", supplier.name(),
                "receiptDate", saved.getReceiptDate().toString(),
                "lines", String.valueOf(arriving.size()),
                "journalEntry", saved.getJournalEntryId() == null
                        // Distinguished rather than left blank: "nothing to capitalise" and "posting
                        // missing" are different situations and one of them is a bug.
                        ? "none — everything arrived at zero cost"
                        : String.valueOf(saved.getJournalEntryId())));

        return toView(saved);
    }

    @Override
    @Transactional
    public GoodsReceiptView reverse(long receiptId, LocalDate reversalDate, String reason) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        GoodsReceipt original = load(receiptId);

        if (original.isReversal()) {
            throw new InvalidGoodsReceiptException(
                    "Goods receipt " + receiptId + " is itself the reversal of receipt "
                            + original.getReversalOfId() + ". Reversing it would receive the stock "
                            + "again while claiming to be a correction; record a new delivery "
                            + "instead, so the ledger says what actually happened.");
        }
        receipts.findByReversalOfId(receiptId).ifPresent(existing -> {
            throw new InvalidGoodsReceiptException(
                    "Goods receipt " + receiptId + " has already been reversed by receipt "
                            + existing.getId() + ". Reversing it twice would remove the stock twice.");
        });
        if (matches.existsStandingMatchForReceipt(receiptId)) {
            throw new InvalidGoodsReceiptException(
                    "Goods receipt " + receiptId + " has been matched by a purchase invoice, which "
                            + "cleared GR/IR against it and may have posted a purchase price "
                            + "variance. Reverse the invoice first, or the clearing account is left "
                            + "holding the other half of a delivery that no longer exists.");
        }

        // The lots first, so the loudest refusal — stock has already moved — happens before anything
        // is posted. unreceive() refuses rather than partially undoing, per ADR 0008.
        for (GoodsReceiptLine line : original.getLines()) {
            InventoryLotView lot = inventory.findLotByReceiptLine(line.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Goods receipt line " + line.getId() + " created no lot. Every line does, "
                                    + "by ADR 0004, so this delivery was recorded outside this "
                                    + "service."));
            inventory.unreceive(lot.id());
        }

        Long reversingEntryId = null;
        if (original.getJournalEntryId() != null) {
            long originalEntryId = original.getJournalEntryId();
            reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                    originalEntryId,
                    reversalDate,
                    "Reversal of goods receipt " + receiptId
                            + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()),
                    JournalSource.GOODS_RECEIPT,
                    journal.mirrorOf(originalEntryId))).id();
        }

        GoodsReceipt reversal = new GoodsReceipt(original.getSupplierId(),
                original.getDeliveryNoteNumber(), reversalDate,
                reason == null || reason.isBlank() ? null : reason.trim(), receiptId);
        reversal.postedAs(reversingEntryId);
        GoodsReceipt saved = receipts.save(reversal);

        auditLog.record("goods-receipt.reversed", ENTITY_TYPE, String.valueOf(receiptId), Map.of(
                "reversedBy", String.valueOf(saved.getId()),
                "reversalDate", reversalDate.toString(),
                "posted", String.valueOf(reversingEntryId != null)));

        return toView(saved);
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<GoodsReceiptView> find(long receiptId) {
        return receipts.findById(receiptId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsReceiptView require(long receiptId) {
        return toView(load(receiptId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsReceiptView> ofSupplier(long supplierId) {
        return receipts.findBySupplierIdOrderByReceiptDateAscIdAsc(supplierId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsReceiptView> between(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no deliveries in it.");
        }
        return receipts.findByReceiptDateBetweenOrderByReceiptDateAscIdAsc(from, to).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GoodsReceiptView> findByJournalEntry(long journalEntryId) {
        return receipts.findByJournalEntryId(journalEntryId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GoodsReceiptView> findByLot(long lotId) {
        return inventory.requireLot(lotId).sourceReceiptLine()
                .flatMap(receiptLines::findById)
                .map(line -> toView(line.getReceipt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsReceiptLineView> linesAwaitingInvoice() {
        return receiptLines.findAwaitingInvoice().stream().map(this::toView).toList();
    }

    // ---------------------------------------------------------------------------------------
    // Preparing one line
    // ---------------------------------------------------------------------------------------

    private ArrivingLine prepare(NewGoodsReceiptLine line, SupplierView supplier) {
        ProductView product = products.require(line.productId());
        if (!product.active()) {
            throw new InvalidGoodsReceiptException(
                    "Product '" + product.sku() + "' is inactive, so stock cannot be received against "
                            + "it.");
        }
        if (!product.isStocked()) {
            throw new InvalidGoodsReceiptException(
                    "Product '" + product.sku() + "' has no stock — it is a "
                            + (product.isBundle() ? "bundle" : product.type().toString())
                            + " — so nothing can be delivered against it. A bundle's availability is "
                            + "computed from its components (brief §5).");
        }
        if (product.isSerialTracked() != line.isSerialized()) {
            throw new InvalidGoodsReceiptException(product.isSerialTracked()
                    ? "Product '" + product.sku() + "' is serial-tracked, so a delivery line states "
                            + "one serial number per unit rather than a bare quantity."
                    : "Product '" + product.sku() + "' is pooled stock, so a delivery line states a "
                            + "quantity. Serial numbers would create identities nothing tracks.");
        }

        if (line.purchaseInvoiceLineId() == null) {
            return new ArrivingLine(line, product, line.receivedQuantity(), line.unitCost(), null);
        }

        // The invoice arrived first, so the price is known and this line takes it — which is why this
        // direction can never produce a variance (ADR 0008).
        long invoiceLineId = line.purchaseInvoiceLineId();
        PurchaseInvoiceLine invoiceLine = invoiceLines.findById(invoiceLineId)
                .orElseThrow(() -> PurchaseInvoiceNotFoundException.forLine(invoiceLineId));
        PurchaseInvoice invoice = invoiceLine.getInvoice();

        if (!invoiceLine.isInventory()) {
            throw new InvalidGoodsReceiptException(
                    "Purchase invoice line " + invoiceLineId + " is an expense line, which buys no "
                            + "stock and has nothing to deliver.");
        }
        if (!Objects.equals(invoice.getSupplierId(), supplier.id())) {
            throw new InvalidGoodsReceiptException(
                    "Purchase invoice line " + invoiceLineId + " belongs to a different supplier's "
                            + "invoice. One supplier's delivery cannot clear another's payable.");
        }
        if (invoice.isReversal() || invoices.findByReversalOfId(invoice.getId()).isPresent()) {
            throw new InvalidGoodsReceiptException(
                    "Purchase invoice " + invoice.getId() + " no longer stands, so there is nothing "
                            + "left in GR/IR clearing for this delivery to settle.");
        }
        if (!Objects.equals(invoiceLine.getProductId(), product.id())) {
            throw new InvalidGoodsReceiptException(
                    "This delivery line receives '" + product.sku() + "' against an invoice line for "
                            + "a different product. Matching them would clear GR/IR for goods that "
                            + "never arrived.");
        }

        Quantity alreadyMatched =
                Quantity.of(matches.matchedAgainstInvoiceLine(invoiceLineId));
        Quantity stillOpen = invoiceLine.getQuantity().minus(alreadyMatched);
        if (line.receivedQuantity().compareTo(stillOpen) > 0) {
            throw new InvalidGoodsReceiptException(
                    "Purchase invoice line " + invoiceLineId + " charges for "
                            + invoiceLine.getQuantity() + ", of which " + alreadyMatched
                            + " has already been delivered, so " + line.receivedQuantity()
                            + " cannot be received against it. Receiving more than was invoiced would "
                            + "clear GR/IR below zero. Record the excess as its own unmatched line — "
                            + "the supplier owes us a document for it.");
        }

        return new ArrivingLine(line, product, line.receivedQuantity(),
                invoiceLine.getUnitPrice(), invoiceLineId);
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    /**
     * Debit Inventory once per lot, credit GR/IR clearing against the supplier for the same amount.
     *
     * <p>One pair of lines per lot rather than one aggregate pair: the Inventory debit <em>must</em>
     * carry a lot reference, because Inventory is a Control account whose sub-ledger is Product-Lot,
     * and pairing the credit line by line keeps the two halves of a delivery readable against each
     * other.
     *
     * <p>Returns null when everything arrived at a unit cost of zero. That is a real delivery — a
     * supplier's free sample — which capitalises nothing, and the ledger rightly refuses a zero-amount
     * line.
     */
    private Long post(GoodsReceipt receipt, SupplierView supplier, List<ArrivingLine> arriving,
            List<GoodsReceiptLine> savedLines, Map<Long, Long> lotIdByLine,
            RoundingMode roundingMode) {
        AccountView inventoryAccount = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY);
        AccountView grIr = chartOfAccounts.requireAccount(
                AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING);
        SubLedgerRef supplierRef = SubLedgerRef.supplier(supplier.id());

        List<NewJournalLine> lines = new ArrayList<>();
        for (int i = 0; i < arriving.size(); i++) {
            ArrivingLine line = arriving.get(i);
            GoodsReceiptLine entity = savedLines.get(i);
            Money value = line.unitCost().extend(line.quantity(), roundingMode);
            if (!value.isPositive()) {
                continue;
            }
            SubLedgerRef lotRef = SubLedgerRef.inventoryLot(lotIdByLine.get(entity.getId()));
            lines.add(NewJournalLine.debit(inventoryAccount.id(), value)
                    .forSubLedger(lotRef)
                    .describedAs(line.quantity() + " x " + line.product().sku()));
            lines.add(NewJournalLine.credit(grIr.id(), value)
                    .forSubLedger(supplierRef)
                    .describedAs(line.quantity() + " x " + line.product().sku()));
        }
        if (lines.isEmpty()) {
            return null;
        }

        String description = receipt.getDescription() != null
                ? receipt.getDescription()
                : "Goods receipt from " + supplier.name()
                        + (receipt.getDeliveryNoteNumber() == null
                                ? "" : " (note " + receipt.getDeliveryNoteNumber() + ")");

        return journal.post(NewJournalEntry.of(
                receipt.getReceiptDate(), description, JournalSource.GOODS_RECEIPT, lines)).id();
    }

    // ---------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------

    private GoodsReceipt load(long receiptId) {
        return receipts.findById(receiptId)
                .orElseThrow(() -> new GoodsReceiptNotFoundException(receiptId));
    }

    private GoodsReceiptView toView(GoodsReceipt receipt) {
        SupplierView supplier = suppliers.require(receipt.getSupplierId());
        List<GoodsReceiptLineView> lineViews =
                receipt.getLines().stream().map(this::toView).toList();

        Currency currency = lineViews.isEmpty()
                ? Money.EUR : lineViews.getFirst().unitCost().currency();
        Money total = Money.zero(currency);
        for (GoodsReceiptLineView line : lineViews) {
            total = total.plus(line.valueAt(RoundingMode.HALF_UP));
        }

        return new GoodsReceiptView(
                receipt.getId(),
                supplier.id(),
                supplier.name(),
                receipt.getDeliveryNoteNumber(),
                receipt.getReceiptDate(),
                receipt.getDescription(),
                total,
                receipt.getJournalEntryId(),
                receipt.getReversalOfId(),
                receipts.findByReversalOfId(receipt.getId())
                        .map(GoodsReceipt::getId).orElse(null),
                lineViews);
    }

    private GoodsReceiptLineView toView(GoodsReceiptLine line) {
        InventoryLotView lot = inventory.findLotByReceiptLine(line.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Goods receipt line " + line.getId() + " created no lot. Every line does, by "
                                + "ADR 0004, so this delivery was recorded outside this service."));
        Quantity matched = Quantity.of(matches.matchedAgainstReceiptLine(line.getId()));

        return new GoodsReceiptLineView(
                line.getId(),
                line.getLineNumber(),
                line.getProductId(),
                lot.productSku(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getLocation(),
                lot.id(),
                lot.units().stream()
                        .map(gr.novotrade.novocore.core.api.inventory.SerializedUnitView::serialNumber)
                        .toList(),
                line.getPurchaseInvoiceLineId(),
                matched,
                line.getQuantity().minus(matched));
    }

    /** One request line with its product resolved and its cost settled. */
    private record ArrivingLine(
            NewGoodsReceiptLine request,
            ProductView product,
            Quantity quantity,
            UnitCost unitCost,
            Long invoiceLineId) {
    }
}
