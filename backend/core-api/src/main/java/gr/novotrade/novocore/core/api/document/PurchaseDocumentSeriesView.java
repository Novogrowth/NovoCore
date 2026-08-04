package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;
import java.util.Optional;

/**
 * A numbering series of a purchase document type.
 *
 * <p>⚠️ <strong>There is deliberately no channel here, and its absence is a decision rather than an
 * omission.</strong> Channel is where a <em>sale</em> came from; it never applies to a purchase. A
 * nullable column that can only ever be null invites someone to fill it, and a purchase series
 * carrying {@code ECOMMERCE} would be storable, meaningless, and indistinguishable from data. The
 * sales series has one and this does not, which is the whole difference between the two records.
 *
 * <p>Numbers are recorded and never generated, exactly as for {@link SalesDocumentSeriesView}.
 *
 * @param inUse whether any recorded purchase document names this series — the predicate that
 *     freezes {@code abbreviation}, {@code documentTypeId} and {@code getsMark}, as on
 *     {@link SalesDocumentSeriesView}.
 *     <p>⚠️⚠️ <strong>It is ALWAYS FALSE today, and that is a fact about the schema rather than
 *     about the data.</strong> Measured 2026-08-04: the only foreign key anywhere referencing
 *     {@code purchase_document_series} is its own {@code transformable_into_series_id}. No purchase
 *     document carries a series — {@code purchase_document_type} becomes mandatory at <strong>F6</strong>,
 *     and a series reference would arrive with it.
 *     <p>⚠️ <strong>So this freeze is unreachable, and it will become reachable silently.</strong>
 *     That is the exact shape {@code CLAUDE.md} names after R1b: a rule that agrees with itself only
 *     because of what the data happens to look like today. {@code DocumentReferenceGraphIT} pins the
 *     referencing set so <strong>F6 cannot add the column without a red build naming this field.</strong>
 *
 * @param sortCode ⚠️ <strong>Ordering only, and not an identifier.</strong> Assigned by the business
 *     so the list an employee sees is in a sensible order. <strong>Freely editable</strong> — unlike
 *     the abbreviation, because it appears on no document and carries no legal meaning — and never
 *     derived from Prosvasis Go's numbers. An {@code int}, because a text sort puts {@code 1000}
 *     before {@code 900}. See {@code V34}.
 */
public record PurchaseDocumentSeriesView(
        long id,
        @Mandatory String abbreviation,
        @Mandatory String description,
        long documentTypeId,
        @Mandatory String documentTypeDescription,
        boolean getsMark,
        Long transformableIntoSeriesId,
        int sortCode,
        boolean inUse,
        boolean active) {

    public PurchaseDocumentSeriesView {
        Objects.requireNonNull(abbreviation, "abbreviation");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(documentTypeDescription, "documentTypeDescription");
    }

    public Optional<Long> transformableIntoSeriesIdIfAny() {
        return Optional.ofNullable(transformableIntoSeriesId);
    }
}
