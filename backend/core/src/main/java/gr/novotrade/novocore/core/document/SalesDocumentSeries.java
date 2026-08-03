package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A numbering series of a sales document type.
 *
 * <p>The document type is a real {@code @ManyToOne} — unlike the AADE reference on the type itself
 * — because a series and its type are one slice of the core and a series is meaningless without
 * knowing what kind of document it numbers. The service reads the type's description inside the
 * transaction and materialises it into the view, so no lazy association escapes to a caller.
 *
 * <p>⚠️ <strong>There is no number field and no counter.</strong> Novocore records what the issuing
 * system printed. A {@code nextNumber()} here would be the first half of a gap-prevention problem
 * that belongs at step 40 and must not be acquired early.
 */
@Entity
@Table(name = "sales_document_series")
class SalesDocumentSeries extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "abbreviation", nullable = false, length = 20)
    private String abbreviation;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private SalesDocumentType documentType;

    /** Null means this series is not a sales channel — the self-supply series are not. */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 30)
    private SalesChannel channel;

    @Column(name = "gets_mark", nullable = false)
    private boolean getsMark;

    /**
     * A plain id and not a {@code @ManyToOne}, deliberately: the association is self-referencing
     * and a mapped one would let a caller walk a transformation chain that is only ever meant to
     * be read one step deep.
     */
    @Column(name = "transformable_into_series_id")
    private Long transformableIntoSeriesId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected SalesDocumentSeries() {
    }

    SalesDocumentSeries(String abbreviation, String description, SalesDocumentType documentType,
            SalesChannel channel, boolean getsMark, Long transformableIntoSeriesId) {
        this.abbreviation = abbreviation;
        this.description = description;
        this.documentType = documentType;
        this.channel = channel;
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

    SalesDocumentType getDocumentType() {
        return documentType;
    }

    SalesChannel getChannel() {
        return channel;
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

    void changeChannel(SalesChannel newChannel) {
        this.channel = newChannel;
    }

    void mapTransformationTarget(Long targetSeriesId) {
        this.transformableIntoSeriesId = targetSeriesId;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
