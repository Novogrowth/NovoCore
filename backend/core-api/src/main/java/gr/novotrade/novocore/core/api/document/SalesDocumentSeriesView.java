package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;
import java.util.Optional;

/**
 * A numbering series of a sales document type — ΑΛΠ, ΑΛΠW, ΤΠΔΑ.
 *
 * <p>⚠️ <strong>Novocore records numbers; it does not generate them.</strong> There is no sequence,
 * no counter and no allocation behind a series. Legal issuance runs through Prosvasis Go today and
 * a certified Πάροχος at step 40, and the document receives its number and its ΜΑΡΚ there.
 * "Integers from 1, continuous" is an expectation about what the issuing system produces, recorded
 * rather than enforced; gap detection belongs with step 25, Clearing Checks.
 *
 * @param channel ⚠️ <strong>Empty means this series is not a sales channel</strong>, which the
 *     self-supply series (Στοιχείο Αυτοπαράδοσης / Ιδιοχρησιμοποίησης) genuinely are not — the
 *     customer is the issuer and there is no channel to attribute.
 *     <p>⚠️ In <strong>R1b</strong> this becomes authoritative: an invoice's channel comes from its
 *     series rather than being independently settable, so ΑΛΠW being the web series makes an
 *     invoice in it a web sale <em>by definition</em> rather than by someone remembering to tick a
 *     box. {@code sales_invoice.channel} is {@code NOT NULL} and that constraint is
 *     <strong>not</strong> to be relaxed for the channel-less series: R1b refuses to record an
 *     invoice against one, because self-supply has no posting rule yet and the constraint is what
 *     holds that question open rather than papering over it. R3 resolves both together.
 * @param getsMark whether a document in this series receives a ΜΑΡΚ. False for operational series,
 *     whose documents never reach AADE at all.
 * @param transformableIntoSeriesId the series a document here may be transformed into — correcting
 *     a mistake must produce the correct series or a return document in <em>one</em> action, never
 *     re-keyed. ⚠️ Only the allowed-target reference is stored; the behaviour needs the Go adapter.
 * @param inUse whether a sales invoice names this series. ⚠️ <strong>This is the predicate that
 *     freezes {@code abbreviation}, {@code documentTypeId} and {@code getsMark}</strong>, added in
 *     R2: those three are correctable while nothing has been recorded here and refused afterwards,
 *     because the abbreviation is what appears on a document, the type decides whether recording one
 *     consumed inventory, and the flag asserts a fact about documents that already exist.
 *     <p>⚠️ Being another series' <em>transformation target</em> deliberately does NOT count. That
 *     reference is by id and survives any of the three changing.
 *     <p>⚠️ A <strong>reversed</strong> invoice counts. It was recorded, it is in the journal, and
 *     its number is in the books — "recorded" is not "standing".
 *     <p>⚠️ It is a flag and not a sentence <strong>on purpose</strong>: the screen renders the
 *     reason, because the backend localises nothing (Q47(b)) and a server-composed reason would be
 *     English prose beside translated labels on a Greek UI.
 */
public record SalesDocumentSeriesView(
        long id,
        @Mandatory String abbreviation,
        @Mandatory String description,
        long documentTypeId,
        @Mandatory String documentTypeDescription,
        SalesChannel channel,
        boolean getsMark,
        Long transformableIntoSeriesId,
        boolean inUse,
        boolean active) {

    public SalesDocumentSeriesView {
        Objects.requireNonNull(abbreviation, "abbreviation");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(documentTypeDescription, "documentTypeDescription");
    }

    /** Empty where the series is not a sales channel. */
    public Optional<SalesChannel> channelIfAny() {
        return Optional.ofNullable(channel);
    }

    public Optional<Long> transformableIntoSeriesIdIfAny() {
        return Optional.ofNullable(transformableIntoSeriesId);
    }
}
