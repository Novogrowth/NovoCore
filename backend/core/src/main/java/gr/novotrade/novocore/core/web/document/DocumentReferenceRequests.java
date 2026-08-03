package gr.novotrade.novocore.core.web.document;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * The request bodies the document reference-data routes share.
 *
 * <p><strong>Shared on purpose, and this is not the collision 8a spent a step fixing.</strong> That
 * one was several <em>different</em> records reduced to a single schema name — most damagingly
 * {@code NameRequest}, whose seven records differ in exactly the property the spec publishes, so
 * the merged schema would have been wrong for at least two of its nine operations. Here there is
 * one record per shape, used by every route that means that shape, which is what
 * {@code ReversalCommand} already does for six reversal routes: one statement covers all of them,
 * and a seventh gets it for free.
 *
 * <p>⚠️ <strong>Every field here is mandatory, and the routes that "clear" something are DELETEs
 * rather than a PUT of null.</strong> That follows {@code VatClassController}'s reduced-counterpart
 * pair and its stated reason: the resource being removed is the mapping itself, and a body carrying
 * only {@code null} says nothing a caller can be held to.
 */
final class DocumentReferenceRequests {

    private DocumentReferenceRequests() {
    }

    /** The description of a document type, series or delivery method. */
    record DocumentDescriptionRequest(@Mandatory String description) {

        DocumentDescriptionRequest {
            Required.text(description, "description");
        }
    }

    /**
     * Both stock flags at once, because they are one decision.
     *
     * <p>Two separate routes would allow an incoherent intermediate state — transfers stock but
     * does not affect it — to be saved and then activated. Boxed, so an omitted field is a 400
     * naming it rather than arriving as {@code false} and silently deciding that stock does not
     * move.
     */
    record StockBehaviourRequest(
            @Mandatory Boolean affectsStock,
            @Mandatory Boolean transfersStock) {

        StockBehaviourRequest {
            Required.field(affectsStock, "affectsStock");
            Required.field(transfersStock, "transfersStock");
        }
    }

    /** Whether documents of this type must reach AADE. */
    record MydataTransmissionRequest(@Mandatory Boolean required) {

        MydataTransmissionRequest {
            Required.field(required, "required");
        }
    }

    /**
     * Points a document type at an AADE invoice type.
     *
     * <p>Clearing the reference is {@code DELETE} on the same path, not a null here — a document
     * type with no AADE code is an ordinary state (six of the owner's nineteen), so removing the
     * mapping is a real operation rather than a field being blanked.
     */
    record AadeInvoiceTypeMappingRequest(@Mandatory Long aadeInvoiceTypeId) {

        AadeInvoiceTypeMappingRequest {
            Required.field(aadeInvoiceTypeId, "aadeInvoiceTypeId");
        }
    }

    /**
     * The sales channel a series represents.
     *
     * <p>⚠️ Sales only. There is no purchase equivalent and there is no purchase route, because
     * channel is where a <em>sale</em> came from.
     */
    record SeriesChannelRequest(@Mandatory SalesChannel channel) {

        SeriesChannelRequest {
            Required.field(channel, "channel");
        }
    }

    /** Which series a document may be transformed into. */
    record TransformationTargetRequest(@Mandatory Long targetSeriesId) {

        TransformationTargetRequest {
            Required.field(targetSeriesId, "targetSeriesId");
        }
    }
}
