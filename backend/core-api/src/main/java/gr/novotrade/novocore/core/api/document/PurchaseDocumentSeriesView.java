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
 */
public record PurchaseDocumentSeriesView(
        long id,
        @Mandatory String abbreviation,
        @Mandatory String description,
        long documentTypeId,
        @Mandatory String documentTypeDescription,
        boolean getsMark,
        Long transformableIntoSeriesId,
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
