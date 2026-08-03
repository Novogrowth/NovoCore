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

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected SalesDocumentType() {
    }

    SalesDocumentType(String description, Boolean affectsStock, Boolean transfersStock,
            boolean requiresMydataTransmission, Long aadeInvoiceTypeId, boolean active) {
        this.description = description;
        this.affectsStock = affectsStock;
        this.transfersStock = transfersStock;
        this.requiresMydataTransmission = requiresMydataTransmission;
        this.aadeInvoiceTypeId = aadeInvoiceTypeId;
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
