package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One of the business's own sales document types.
 *
 * <p>The reference to the AADE codification is a plain id rather than a {@code @ManyToOne}, for the
 * reason {@code Product} holds its VAT class as an id: it is a cross-aggregate reference into a
 * different slice of the core, and the association a JPA mapping would create is not one this
 * aggregate owns.
 *
 * <p>⚠️ {@link #affectsStock} and {@link #transfersStock} are boxed on purpose. A primitive would
 * make "undecided" unrepresentable and turn every unanswered question into a {@code false} that
 * reads as a decision.
 */
@Entity
@Table(name = "sales_document_type")
class SalesDocumentType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    @Column(name = "affects_stock")
    private Boolean affectsStock;

    @Column(name = "transfers_stock")
    private Boolean transfersStock;

    @Column(name = "requires_mydata_transmission", nullable = false)
    private boolean requiresMydataTransmission;

    @Column(name = "aade_invoice_type_id")
    private Long aadeInvoiceTypeId;

    /**
     * ⚠️ <strong>Ordering only. Not an identifier, and the name is what keeps it that way.</strong>
     *
     * <p>The owner assigns these so that the list an employee sees when recording a document is in
     * a sensible order. It is <strong>freely editable</strong> — deliberately not the
     * editable-while-unused freeze R2 put on a series' abbreviation, because an abbreviation appears
     * on a document and this appears on nothing. It carries no legal meaning, is transmitted
     * nowhere, and is <strong>never derived from Prosvasis Go's numbers</strong>, which are Go's
     * internal ids and belong in an adapter mapping table.
     *
     * <p>An {@code int} rather than a string because a text sort puts {@code 1000} before
     * {@code 900} — for a column whose whole purpose is ordering that is the column failing at its
     * job. See {@code V34}.
     */
    @Column(name = "sort_code", nullable = false)
    private int sortCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected SalesDocumentType() {
    }

    SalesDocumentType(String description, Boolean affectsStock, Boolean transfersStock,
            boolean requiresMydataTransmission, Long aadeInvoiceTypeId, int sortCode,
            boolean active) {
        this.description = description;
        this.affectsStock = affectsStock;
        this.transfersStock = transfersStock;
        this.requiresMydataTransmission = requiresMydataTransmission;
        this.aadeInvoiceTypeId = aadeInvoiceTypeId;
        this.sortCode = sortCode;
        this.active = active;
    }

    Long getId() {
        return id;
    }

    String getDescription() {
        return description;
    }

    Boolean getAffectsStock() {
        return affectsStock;
    }

    Boolean getTransfersStock() {
        return transfersStock;
    }

    boolean isRequiresMydataTransmission() {
        return requiresMydataTransmission;
    }

    Long getAadeInvoiceTypeId() {
        return aadeInvoiceTypeId;
    }

    boolean isActive() {
        return active;
    }

    int getSortCode() {
        return sortCode;
    }

    /** Reordering is a normal act, not a correction — see the field's note. */
    void changeSortCode(int newSortCode) {
        this.sortCode = newSortCode;
    }

    /** True while either stock flag is undecided. Such a type cannot be active. */
    boolean isDraft() {
        return affectsStock == null || transfersStock == null;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    /** Both at once, because they are one decision — see the service interface. */
    void changeStockBehaviour(boolean nowAffectsStock, boolean nowTransfersStock) {
        this.affectsStock = nowAffectsStock;
        this.transfersStock = nowTransfersStock;
    }

    void changeMydataTransmissionRequired(boolean required) {
        this.requiresMydataTransmission = required;
    }

    void mapToAadeInvoiceType(Long newAadeInvoiceTypeId) {
        this.aadeInvoiceTypeId = newAadeInvoiceTypeId;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
