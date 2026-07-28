package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A supplier's invoice, recorded rather than issued.
 *
 * <p>Immutable once posted (Q13, ADR 0006): it is somebody else's document, carrying their number,
 * and from roadmap phase 7 it usually arrives through the myDATA import rather than being typed.
 * Correction is a reversing document — {@link #reversalOfId} — which carries no lines of its own,
 * because everything a reader needs is on the original and duplicating its lines would give one
 * purchase two statements of itself.
 *
 * <p><strong>The supplier is a plain id.</strong> {@code Supplier} is package-private in its own
 * slice, which is the boundary ADR 0003 draws inside the core as well as around it — the same reason
 * {@code ChargeType} holds ids rather than associations. Validated through {@code SupplierService},
 * the published interface an adapter would use, and the foreign key still exists in the database.
 */
@Entity
@Table(name = "purchase_invoice")
class PurchaseInvoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** The supplier's document number. Unique per supplier among non-reversal documents. */
    @Column(name = "supplier_invoice_number", nullable = false, length = 60)
    private String supplierInvoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "description", length = 500)
    private String description;

    /** Never null: an invoice always creates a payable. */
    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @OneToMany(mappedBy = "invoice", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<PurchaseInvoiceLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected PurchaseInvoice() {
    }

    PurchaseInvoice(long supplierId, String supplierInvoiceNumber, LocalDate invoiceDate,
            String description, Long reversalOfId) {
        this.supplierId = supplierId;
        this.supplierInvoiceNumber = supplierInvoiceNumber;
        this.invoiceDate = invoiceDate;
        this.description = description;
        this.reversalOfId = reversalOfId;
    }

    PurchaseInvoiceLine addLine(PurchaseInvoiceLine line) {
        line.attachTo(this, lines.size());
        lines.add(line);
        return line;
    }

    void postedAs(long entryId) {
        this.journalEntryId = entryId;
    }

    Long getId() {
        return id;
    }

    Long getSupplierId() {
        return supplierId;
    }

    String getSupplierInvoiceNumber() {
        return supplierInvoiceNumber;
    }

    LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    String getDescription() {
        return description;
    }

    Long getJournalEntryId() {
        return journalEntryId;
    }

    Long getReversalOfId() {
        return reversalOfId;
    }

    boolean isReversal() {
        return reversalOfId != null;
    }

    /** Sorted here as well as by {@code @OrderBy}, for the reason {@code InventoryLot.getUnits} gives. */
    List<PurchaseInvoiceLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(PurchaseInvoiceLine::getLineNumber))
                .toList();
    }
}
