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
    protected PurchaseDocumentSeries() {
    }

    PurchaseDocumentSeries(String abbreviation, String description,
            PurchaseDocumentType documentType, boolean getsMark, Long transformableIntoSeriesId,
            int sortCode) {
        this.abbreviation = abbreviation;
        this.description = description;
        this.documentType = documentType;
        this.getsMark = getsMark;
        this.transformableIntoSeriesId = transformableIntoSeriesId;
        this.sortCode = sortCode;
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

    int getSortCode() {
        return sortCode;
    }

    /** Reordering is a normal act, not a correction — see the field's note. */
    void changeSortCode(int newSortCode) {
        this.sortCode = newSortCode;
    }

    /**
     * ⚠️ Correcting a typo, never renaming a series documents were recorded under. The service
     * refuses once anything names this row; this method assumes that check has already been made.
     */
    void changeAbbreviation(String newAbbreviation) {
        this.abbreviation = newAbbreviation;
    }

    void changeDocumentType(PurchaseDocumentType newDocumentType) {
        this.documentType = newDocumentType;
    }

    void changeGetsMark(boolean nowGetsMark) {
        this.getsMark = nowGetsMark;
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
