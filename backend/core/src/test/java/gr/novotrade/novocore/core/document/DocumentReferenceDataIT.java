package gr.novotrade.novocore.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService;
import gr.novotrade.novocore.core.api.document.DeliveryMethodService;
import gr.novotrade.novocore.core.api.document.DeliveryMethodView;
import gr.novotrade.novocore.core.api.document.DocumentSeriesNotFoundException;
import gr.novotrade.novocore.core.api.document.DocumentTypeNotFoundException;
import gr.novotrade.novocore.core.api.document.InvalidDeliveryMethodException;
import gr.novotrade.novocore.core.api.document.InvalidDocumentSeriesException;
import gr.novotrade.novocore.core.api.document.InvalidDocumentTypeException;
import gr.novotrade.novocore.core.api.document.NewDeliveryMethod;
import gr.novotrade.novocore.core.api.document.NewPurchaseDocumentSeries;
import gr.novotrade.novocore.core.api.document.NewPurchaseDocumentType;
import gr.novotrade.novocore.core.api.document.NewSalesDocumentSeries;
import gr.novotrade.novocore.core.api.document.NewSalesDocumentType;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentTypeService;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentTypeView;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesView;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeService;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeView;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The business's own document reference data — types, series and delivery methods.
 *
 * <h2>⚠️ These tables ship EMPTY, and that is the first thing asserted</h2>
 *
 * <p>The owner's nineteen document types are deliberately not seeded and their Go→AADE mappings are
 * deliberately not inferred: he creates them through R2's screens, choosing each AADE type himself.
 * An inferred mapping would be a guess written into a statutory field.
 *
 * <p>Every fixture below is created by the test that needs it and named with a {@code (test)}
 * suffix, because these tests share one non-transactional database with their neighbours. There is
 * no seed to collide with, which is the one convenience of a table that ships empty.
 */
class DocumentReferenceDataIT extends AbstractCoreIntegrationTest {

    @Autowired
    private SalesDocumentTypeService salesTypes;
    @Autowired
    private PurchaseDocumentTypeService purchaseTypes;
    @Autowired
    private SalesDocumentSeriesService salesSeries;
    @Autowired
    private PurchaseDocumentSeriesService purchaseSeries;
    @Autowired
    private DeliveryMethodService deliveryMethods;
    @Autowired
    private AadeInvoiceTypeService aadeInvoiceTypes;

    /** Distinct names per test, since the tables are shared and nothing is rolled back. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private String unique(String stem) {
        return stem + " (test " + SEQUENCE.incrementAndGet() + ")";
    }

    // -------------------------------------------------------------------------------------------
    // The two-layer model
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ a document type may exist with NO AADE invoice type, which is the whole point")
    void aDocumentTypeNeedNotHaveAnAadeCode() {
        // Six of the owner's nineteen are exactly this: Προσφορά, Δελτίο Αποστολής, Δελτίο
        // ποσοτικής παραλαβής, Παραγγελία, Δελτίο Παραλαβής, ΔΑ Αποστολής Σε Προμηθευτή. They are
        // operational documents, not tax documents. A model in which the AADE code IS the row
        // cannot represent them, which is what disproved the previous design.
        SalesDocumentTypeView quote = salesTypes.create(new NewSalesDocumentType(
                unique("Προσφορά"), false, false, false, null));

        assertThat(quote.aadeInvoiceTypeIdIfAny()).isEmpty();
        assertThat(quote.aadeInvoiceTypeCode()).isNull();
        assertThat(quote.requiresMydataTransmission()).isFalse();
        assertThat(quote.active()).isTrue();
    }

    @Test
    @DisplayName("a sales type may only name an issuer-side AADE code")
    void salesTypesAreRefusedARecipientCode() {
        long rentExpense = aadeInvoiceTypes.requireByCode("16.1").id();   // Ενοίκιο Έξοδο
        long salesInvoice = aadeInvoiceTypes.requireByCode("1.1").id();   // Τιμολόγιο Πώλησης

        // ⚠️ Nothing in AADE's own artefacts stops this: the XSD has ONE enumeration covering both
        // directions, and the sales/purchase split is ours, read from annex 8.1's group headings.
        // This service is where that split is enforced.
        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> salesTypes.create(new NewSalesDocumentType(
                        unique("Wrong side"), false, false, true, rentExpense)))
                .withMessageContaining("16.1")
                .withMessageContaining("issues");

        SalesDocumentTypeView correct = salesTypes.create(new NewSalesDocumentType(
                unique("Τιμολόγιο"), false, false, true, salesInvoice));
        assertThat(correct.aadeInvoiceTypeCode()).isEqualTo("1.1");
    }

    @Test
    @DisplayName("a purchase type may only name a recipient-side AADE code")
    void purchaseTypesAreRefusedAnIssuerCode() {
        long salesInvoice = aadeInvoiceTypes.requireByCode("1.1").id();
        long intraCommunity = aadeInvoiceTypes.requireByCode("14.1").id();

        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> purchaseTypes.create(new NewPurchaseDocumentType(
                        unique("Wrong side"), false, false, true, salesInvoice)))
                .withMessageContaining("1.1")
                .withMessageContaining("receives");

        PurchaseDocumentTypeView correct = purchaseTypes.create(new NewPurchaseDocumentType(
                unique("Ενδοκοινοτικές"), true, false, true, intraCommunity));
        assertThat(correct.aadeInvoiceTypeCode()).isEqualTo("14.1");
    }

    @Test
    @DisplayName("neither side may name one of the six entity-adjusting codes")
    void entityAdjustingCodesBelongToNoDocumentList() {
        long payroll = aadeInvoiceTypes.requireByCode("17.1").id();

        // These are the entity's own journal entries, with no counterparty at all. They are rows in
        // the codification because AADE publishes them; they are in no document list because they
        // are not documents issued to or received from anyone.
        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> salesTypes.create(new NewSalesDocumentType(
                        unique("Payroll sales"), false, false, true, payroll)))
                .withMessageContaining("ENTITY_ADJUSTING");

        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> purchaseTypes.create(new NewPurchaseDocumentType(
                        unique("Payroll purchase"), false, false, true, payroll)))
                .withMessageContaining("ENTITY_ADJUSTING");
    }

    // -------------------------------------------------------------------------------------------
    // Drafts — a null stock flag is "undecided", never false
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ a type created without its stock flags is an inactive DRAFT, not a false")
    void undecidedStockBehaviourProducesADraft() {
        SalesDocumentTypeView draft = salesTypes.create(new NewSalesDocumentType(
                unique("Undecided"), null, null, true, null));

        assertThat(draft.affectsStock())
                .as("null, and not false — a false would read as a decision that stock does not "
                        + "move and be indistinguishable from a field nobody answered")
                .isNull();
        assertThat(draft.isDraft()).isTrue();
        assertThat(draft.active())
                .as("the database refuses an active row with an undecided flag")
                .isFalse();

        assertThat(salesTypes.drafts()).extracting(SalesDocumentTypeView::id).contains(draft.id());
        assertThat(salesTypes.active()).extracting(SalesDocumentTypeView::id)
                .doesNotContain(draft.id());

        // And it cannot be activated until the question is answered, with the message saying why.
        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> salesTypes.reactivate(draft.id()))
                .withMessageContaining("stock behaviour is undecided");

        salesTypes.changeStockBehaviour(draft.id(), true, true);
        salesTypes.reactivate(draft.id());
        assertThat(salesTypes.require(draft.id()).active()).isTrue();
        assertThat(salesTypes.require(draft.id()).isDraft()).isFalse();
    }

    @Test
    @DisplayName("transfers-stock without affects-stock is refused as incoherent")
    void transferringStockImpliesAffectingIt() {
        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> salesTypes.create(new NewSalesDocumentType(
                        unique("Incoherent"), false, true, true, null)))
                .withMessageContaining("necessarily affects it");
    }

    @Test
    @DisplayName("⚠️ affectsStock is meaningful on the PURCHASE side — the 2041 case")
    void purchaseSideStockBehaviourIsNotAMirror() {
        // The owner's own evidence, and the clearest justification the column has: 2062 ΤΔΑΑ is
        // used daily and brings stock in with a payable behind it; 2041 Δελτίο Παραλαβής is the
        // exception — a machine sent to a supplier for service and returned. A purchase document
        // bringing stock IN with no payable behind it.
        PurchaseDocumentTypeView receipt = purchaseTypes.create(new NewPurchaseDocumentType(
                unique("Δελτίο Παραλαβής"), true, false, false, null));

        assertThat(receipt.affectsStock()).isTrue();
        assertThat(receipt.requiresMydataTransmission())
                .as("it is not a tax document")
                .isFalse();
        assertThat(receipt.aadeInvoiceTypeIdIfAny()).isEmpty();
    }

    @Test
    @DisplayName("deactivating a type never invalidates it, and reactivating a decided one works")
    void deactivationIsAlwaysAllowed() {
        SalesDocumentTypeView type = salesTypes.create(new NewSalesDocumentType(
                unique("Retirable"), true, true, true, null));

        salesTypes.deactivate(type.id());
        assertThat(salesTypes.require(type.id()).active()).isFalse();
        assertThat(salesTypes.require(type.id()).isDraft())
                .as("inactive but decided — a retired type, not a draft")
                .isFalse();

        salesTypes.reactivate(type.id());
        assertThat(salesTypes.require(type.id()).active()).isTrue();
    }

    @Test
    @DisplayName("the AADE mapping can be set and cleared, and clearing is an ordinary state")
    void theAadeMappingIsOptionalInBothDirections() {
        long salesInvoice = aadeInvoiceTypes.requireByCode("1.1").id();
        SalesDocumentTypeView type = salesTypes.create(new NewSalesDocumentType(
                unique("Mappable"), true, true, true, salesInvoice));

        assertThat(type.aadeInvoiceTypeCode()).isEqualTo("1.1");

        SalesDocumentTypeView cleared = salesTypes.mapToAadeInvoiceType(type.id(), null);
        assertThat(cleared.aadeInvoiceTypeIdIfAny()).isEmpty();
        assertThat(cleared.aadeInvoiceTypeCode()).isNull();

        long retail = aadeInvoiceTypes.requireByCode("11.1").id();
        assertThat(salesTypes.mapToAadeInvoiceType(type.id(), retail).aadeInvoiceTypeCode())
                .isEqualTo("11.1");
    }

    @Test
    @DisplayName("a duplicate description is refused with its reason")
    void duplicateDescriptionIsRefused() {
        String name = unique("Duplicated");
        salesTypes.create(new NewSalesDocumentType(name, true, true, true, null));

        assertThatExceptionOfType(InvalidDocumentTypeException.class)
                .isThrownBy(() -> salesTypes.create(
                        new NewSalesDocumentType(name, true, true, true, null)))
                .withMessageContaining("already exists");
    }

    // -------------------------------------------------------------------------------------------
    // Series
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ a sales series may have NO channel, and that is a configuration not a gap")
    void aSeriesMayNotBeASalesChannel() {
        SalesDocumentTypeView selfSupply = salesTypes.create(new NewSalesDocumentType(
                unique("Αυτοπαράδοση"), true, false, true,
                aadeInvoiceTypes.requireByCode("6.1").id()));

        SalesDocumentSeriesView series = salesSeries.create(new NewSalesDocumentSeries(
                unique("ΑΥΤ").replace(" ", ""), unique("Self-supply series"),
                selfSupply.id(), null, true, null));

        assertThat(series.channelIfAny())
                .as("the customer is the issuer; there is no sales channel to attribute")
                .isEmpty();

        // And it can be set and cleared. ⚠️ Clearing is a real operation rather than blanking a
        // field: in R1b a channel-less series is what an invoice is REFUSED against, because
        // self-supply has no posting rule yet.
        SalesDocumentSeriesView withChannel =
                salesSeries.changeChannel(series.id(), SalesChannel.ECOMMERCE);
        assertThat(withChannel.channelIfAny()).contains(SalesChannel.ECOMMERCE);

        assertThat(salesSeries.changeChannel(series.id(), null).channelIfAny()).isEmpty();
    }

    @Test
    @DisplayName("⚠️ the purchase series service has no channel operation at all")
    void purchaseSeriesHaveNoChannel() {
        // Asserted rather than left to a missing column, because "there is no method" is exactly
        // the kind of absence that gets undone by somebody adding one for symmetry. Channel is
        // where a SALE came from and never applies to a purchase.
        assertThat(PurchaseDocumentSeriesService.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("channel"));
    }

    @Test
    @DisplayName("⚠️ no service anywhere hands out a document number")
    void novocoreRecordsNumbersAndDoesNotGenerateThem() {
        // Legal issuance runs through Prosvasis Go today and a Πάροχος at step 40; the document
        // receives its number and its ΜΑΡΚ there. A next-number method would be the first half of
        // a gap-prevention problem that belongs at step 40 and must not be acquired early.
        assertThat(SalesDocumentSeriesService.class.getMethods())
                .noneMatch(method -> {
                    String name = method.getName().toLowerCase();
                    return name.contains("next") || name.contains("allocate")
                            || name.contains("number");
                });
        assertThat(PurchaseDocumentSeriesService.class.getMethods())
                .noneMatch(method -> {
                    String name = method.getName().toLowerCase();
                    return name.contains("next") || name.contains("allocate")
                            || name.contains("number");
                });
    }

    @Test
    @DisplayName("a series cannot transform into itself")
    void aSeriesCannotTransformIntoItself() {
        SalesDocumentTypeView type = salesTypes.create(new NewSalesDocumentType(
                unique("Transformable"), true, true, true, null));
        SalesDocumentSeriesView series = salesSeries.create(new NewSalesDocumentSeries(
                "TR" + SEQUENCE.incrementAndGet(), unique("Series"), type.id(), null, true, null));

        assertThatExceptionOfType(InvalidDocumentSeriesException.class)
                .isThrownBy(() -> salesSeries.mapTransformationTarget(series.id(), series.id()))
                .withMessageContaining("cannot transform into itself");

        SalesDocumentSeriesView target = salesSeries.create(new NewSalesDocumentSeries(
                "TG" + SEQUENCE.incrementAndGet(), unique("Target"), type.id(), null, true, null));
        assertThat(salesSeries.mapTransformationTarget(series.id(), target.id())
                .transformableIntoSeriesIdIfAny()).contains(target.id());
        assertThat(salesSeries.mapTransformationTarget(series.id(), null)
                .transformableIntoSeriesIdIfAny()).isEmpty();
    }

    @Test
    @DisplayName("a series over an unknown document type is refused as not found")
    void seriesNeedsAnExistingType() {
        assertThatExceptionOfType(DocumentTypeNotFoundException.class)
                .isThrownBy(() -> salesSeries.create(new NewSalesDocumentSeries(
                        "NX" + SEQUENCE.incrementAndGet(), unique("Orphan"),
                        999_999L, null, true, null)));

        assertThatExceptionOfType(DocumentSeriesNotFoundException.class)
                .isThrownBy(() -> salesSeries.require(999_999L));
    }

    @Test
    @DisplayName("a duplicate abbreviation is refused, since it is what a document prints")
    void duplicateAbbreviationIsRefused() {
        SalesDocumentTypeView type = salesTypes.create(new NewSalesDocumentType(
                unique("Abbreviated"), true, true, true, null));
        String abbreviation = "AB" + SEQUENCE.incrementAndGet();
        salesSeries.create(new NewSalesDocumentSeries(
                abbreviation, unique("First"), type.id(), null, true, null));

        assertThatExceptionOfType(InvalidDocumentSeriesException.class)
                .isThrownBy(() -> salesSeries.create(new NewSalesDocumentSeries(
                        abbreviation, unique("Second"), type.id(), null, true, null)))
                .withMessageContaining("already exists");
    }

    @Test
    @DisplayName("the purchase series round-trips and narrows by document type")
    void purchaseSeriesRoundTrip() {
        PurchaseDocumentTypeView type = purchaseTypes.create(new NewPurchaseDocumentType(
                unique("ΤΔΑΑ"), true, false, true,
                aadeInvoiceTypes.requireByCode("14.1").id()));

        String abbreviation = "PS" + SEQUENCE.incrementAndGet();
        purchaseSeries.create(new NewPurchaseDocumentSeries(
                abbreviation, unique("Purchase series"), type.id(), true, null));

        assertThat(purchaseSeries.ofDocumentType(type.id()))
                .extracting(view -> view.abbreviation())
                .containsExactly(abbreviation);
        assertThat(purchaseSeries.ofDocumentType(type.id()).getFirst().documentTypeDescription())
                .as("resolved inside the transaction, so no screen ever renders a raw id")
                .isEqualTo(type.description());
    }

    // -------------------------------------------------------------------------------------------
    // Delivery methods
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("delivery methods round-trip, deactivate and refuse a duplicate abbreviation")
    void deliveryMethodLifecycle() {
        String abbreviation = "DM" + SEQUENCE.incrementAndGet();
        DeliveryMethodView courier = deliveryMethods.create(
                new NewDeliveryMethod(abbreviation, unique("ACS courier")));

        assertThat(courier.active()).isTrue();
        assertThat(deliveryMethods.require(courier.id()).abbreviation()).isEqualTo(abbreviation);

        assertThatExceptionOfType(InvalidDeliveryMethodException.class)
                .isThrownBy(() -> deliveryMethods.create(
                        new NewDeliveryMethod(abbreviation, unique("Duplicate"))))
                .withMessageContaining("already exists");

        deliveryMethods.deactivate(courier.id());
        assertThat(deliveryMethods.require(courier.id()).active()).isFalse();
        assertThat(deliveryMethods.active()).extracting(DeliveryMethodView::id)
                .doesNotContain(courier.id());

        deliveryMethods.reactivate(courier.id());
        assertThat(deliveryMethods.require(courier.id()).active()).isTrue();

        String corrected = unique("ACS courier, corrected");
        assertThat(deliveryMethods.describe(courier.id(), corrected).description())
                .isEqualTo(corrected);
    }
}
