package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A numbering series of a purchase document type.
 *
 * <p>⚠️ <strong>No channel field, and its absence is the decision.</strong> Channel is where a
 * <em>sale</em> came from; it never applies to a purchase, so there is no column, no accessor and
 * no route. A nullable one that could only ever be null would invite someone to fill it.
 *
 * <p>No number field and no counter, exactly as for {@link SalesDocumentSeries}.
 */
@Entity
@Table(name = "purchase_document_series")
class PurchaseDocumentSeries extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "abbreviation", nullable = false, length = 20)
    private String abbreviation;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private PurchaseDocumentType documentType;

    @Column(name = "gets_mark", nullable = false)
    private boolean getsMark;

    @Column(name = "transformable_into_series_id")
    private Long transformableIntoSeriesId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected PurchaseDocumentSeries() {
    }

    PurchaseDocumentSeries(String abbreviation, String description,
            PurchaseDocumentType documentType, boolean getsMark, Long transformableIntoSeriesId) {
        this.abbreviation = abbreviation;
        this.description = description;
        this.documentType = documentType;
        this.getsMark = getsMark;
        this.transformableIntoSeriesId = transformableIntoSeriesId;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getAbbreviation() {
        return abbreviation;
    }

    String getDescription() {
        return description;
    }

    PurchaseDocumentType getDocumentType() {
        return documentType;
    }

    boolean isGetsMark() {
        return getsMark;
    }

    Long getTransformableIntoSeriesId() {
        return transformableIntoSeriesId;
    }

    boolean isActive() {
        return active;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void mapTransformationTarget(Long targetSeriesId) {
        this.transformableIntoSeriesId = targetSeriesId;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
