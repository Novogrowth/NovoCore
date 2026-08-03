package gr.novotrade.novocore.core.codification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceGroup;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeNotFoundException;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeView;
import gr.novotrade.novocore.core.api.codification.InvalidAadeInvoiceTypeException;
import gr.novotrade.novocore.core.api.codification.StatutoryCodification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The AADE myDATA invoice-type codification, against the real seeded list (V31).
 *
 * <h2>⭐ The cross-check, and why it is worth a test rather than a comment</h2>
 *
 * <p>The seed has <strong>two sources that cannot check each other by inspection</strong>: the XSD
 * gives 55 codes and no groups; annex 8.1 gives groups and no machine-readable code list. Either
 * could be transcribed wrong and look entirely plausible — which is exactly what happened to an
 * earlier attempt at annex 8.2, where {@code pdftotext} produced a clean two-column table of
 * code/description pairs that were every one of them off by one.
 *
 * <p>Together they check each other. If every group count holds <em>and</em> the total equals the
 * enumeration size, then no code was dropped, none was invented, and none was filed under the wrong
 * heading — because a misfiled code changes two counts at once and a dropped one changes the total.
 *
 * <p>{@link #theSeedIsTheXsdEnumerationExactly()} goes further and reads the XSD itself, so the test
 * compares the database against <strong>the artefact</strong> rather than against a list somebody
 * typed into this file twice.
 */
class AadeInvoiceTypeIT extends AbstractCoreIntegrationTest {

    @Autowired
    private AadeInvoiceTypeService invoiceTypes;

    /** Read visually from a 170 dpi render of annex 8.1, pages 89–93. Never from a text dump. */
    private static final Map<AadeInvoiceGroup, Integer> EXPECTED_GROUP_SIZES = Map.of(
            AadeInvoiceGroup.ISSUER_MATCHED, 28,
            AadeInvoiceGroup.ISSUER_UNMATCHED, 6,
            AadeInvoiceGroup.RECIPIENT_UNMATCHED, 6,
            AadeInvoiceGroup.RECIPIENT_MATCHED, 9,
            AadeInvoiceGroup.ENTITY_ADJUSTING, 6);

    private static final Path XSD = Path.of("..", "..", "docs", "aade", "v2.0.1",
            "v2.0.1 XSDs", "SimpleTypes-v2.0.1.xsd");

    @Test
    @DisplayName("⭐ 28 + 6 + 6 + 9 + 6 = 55, which is the XSD enumeration size exactly")
    void theGroupMapReconcilesWithTheEnumeration() {
        int total = 0;
        for (Map.Entry<AadeInvoiceGroup, Integer> expected : EXPECTED_GROUP_SIZES.entrySet()) {
            assertThat(invoiceTypes.inGroup(expected.getKey()))
                    .as("annex 8.1 group %s", expected.getKey())
                    .hasSize(expected.getValue());
            total += expected.getValue();
        }

        assertThat(total).isEqualTo(55);
        assertThat(invoiceTypes.all())
                .as("every code accounted for by a group, none left over and none invented")
                .hasSize(total);
    }

    @Test
    @DisplayName("the seed is SimpleTypes-v2.0.1.xsd's InvoiceType enumeration, read from the file")
    void theSeedIsTheXsdEnumerationExactly() throws IOException {
        List<String> fromArtefact = invoiceTypeEnumerationFromTheXsd();

        // ⚠️ The negative control for this test. A path that resolves to nothing, a renamed XSD or
        // a changed simpleType name would all produce an empty list, and every assertion below
        // would then pass while comparing the database against nothing at all.
        assertThat(fromArtefact)
                .as("the XSD was not read — this test would otherwise pass vacuously. Check %s",
                        XSD.toAbsolutePath())
                .hasSize(55);

        assertThat(invoiceTypes.all()).extracting(AadeInvoiceTypeView::code)
                .as("the database must carry AADE's enumeration, in AADE's own order")
                .containsExactlyElementsOf(fromArtefact);
    }

    @Test
    @DisplayName("Greek survives the seed, including the two codes with no description in the annex")
    void seededTextIsIntact() {
        assertThat(invoiceTypes.requireByCode("1.1").description()).isEqualTo("Τιμολόγιο Πώλησης");
        assertThat(invoiceTypes.requireByCode("11.1").description()).isEqualTo("ΑΛΠ");
        assertThat(invoiceTypes.requireByCode("17.2").description()).isEqualTo("Αποσβέσεις");

        // ⚠️ Codes 4 and 12 have an EMPTY description cell in annex 8.1. The only text AADE gives
        // them is the group label in the left-hand column, and that is what they carry — read from
        // the artefact rather than invented, and asserted so nobody later mistakes it for a
        // placeholder somebody forgot to fill in.
        assertThat(invoiceTypes.requireByCode("4").description()).isEqualTo("Για Μελλοντική Χρήση");
        assertThat(invoiceTypes.requireByCode("12").description()).isEqualTo("Για Μελλοντική Χρήση");

        // R3 depends on these two being distinct codes under one annex sub-heading.
        assertThat(invoiceTypes.requireByCode("6.1").description())
                .isEqualTo("Στοιχείο Αυτοπαράδοσης");
        assertThat(invoiceTypes.requireByCode("6.2").description())
                .isEqualTo("Στοιχείο Ιδιοχρησιμοποίησης");
    }

    @Test
    @DisplayName("issued is 34 and received is 15; the six entity-adjusting codes are in neither")
    void theSalesPurchaseSplitIsOursAndLeavesSixCodesOut() {
        assertThat(invoiceTypes.issued()).hasSize(34);
        assertThat(invoiceTypes.received()).hasSize(15);

        // ⚠️ 34 + 15 = 49, not 55. The six 17.x codes are the entity's own journal entries and
        // belong to NO document list — which is the finding that dissolved the "third table"
        // blocker rather than being decided by it. Asserted so the gap is a stated property of the
        // codification instead of looking like six missing rows.
        assertThat(invoiceTypes.issued().size() + invoiceTypes.received().size()).isEqualTo(49);
        assertThat(invoiceTypes.inGroup(AadeInvoiceGroup.ENTITY_ADJUSTING))
                .hasSize(6)
                .extracting(AadeInvoiceTypeView::code)
                .containsExactly("17.1", "17.2", "17.3", "17.4", "17.5", "17.6");
    }

    @Test
    @DisplayName("an entity-adjusting code refuses to say which side it is on")
    void entityAdjustingCodesHaveNoSide() {
        AadeInvoiceTypeView payroll = invoiceTypes.requireByCode("17.1");

        assertThat(payroll.group().hasCounterparty()).isFalse();
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(payroll.group()::issuedByUs)
                .withMessageContaining("neither issued to nor received from");
    }

    @Test
    @DisplayName("⚠️ the view carries no accessor that can throw — a serialiser asks every one")
    void theViewHasNoDerivedAccessorThatCanThrow() {
        /*
         * ⚠️ This test exists because R1a shipped the defect it forbids.
         *
         * `AadeInvoiceTypeView` briefly had a one-line `issuedByUs()` delegating to the enum, which
         * throws for the six ENTITY_ADJUSTING codes. Every service-layer test passed —
         * `entityAdjustingCodesHaveNoSide` above even asserted the throw and called it correct —
         * and `GET /api/aade-invoice-types` answered **500** for the whole list, because Jackson
         * serialises a record's no-arg public accessors and called it on all 55 rows.
         *
         * The exception is right where it lives; what was wrong was putting a caller for it on a
         * record that goes on the wire. So the rule is about the record, not about the exception:
         * a serialised view exposes its components and nothing that computes.
         */
        for (var method : AadeInvoiceTypeView.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && java.lang.reflect.Modifier
                    .isPublic(method.getModifiers()) && !method.isSynthetic()) {
                assertThat(RECORD_COMPONENTS)
                        .as("%s is a no-arg public accessor Jackson will call while serialising "
                                + "this view. Only record components may be that.", method.getName())
                        .contains(method.getName());
            }
        }
    }

    /** The five components, which are also exactly what the committed spec documents. */
    private static final List<String> RECORD_COMPONENTS =
            List.of("id", "code", "description", "group", "active",
                    // Object's own, which reflection also reports.
                    "toString", "hashCode");

    @Test
    @DisplayName("codes are returned in the XSD's order, not sorted as text")
    void orderedByTheEnumerationNotAlphabetically() {
        List<String> codes = invoiceTypes.all().stream().map(AadeInvoiceTypeView::code).toList();

        // The codes are dotted strings, so a text sort would put 10.1 before 2.1 and 13.31 before
        // 13.4. Asserting the real neighbours is what catches an ORDER BY code creeping in.
        assertThat(codes.indexOf("2.1")).isLessThan(codes.indexOf("10.1"));
        assertThat(codes.indexOf("13.4")).isLessThan(codes.indexOf("13.30"));
        assertThat(codes.indexOf("13.30")).isLessThan(codes.indexOf("13.31"));
    }

    @Test
    @DisplayName("⚠️ there is no way to author a code — the contract forbids it")
    void thereIsNoCreatePath() {
        assertThat(StatutoryCodification.class).isAssignableFrom(AadeInvoiceTypeService.class);
        assertThat(AadeInvoiceTypeService.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().startsWith("create"));
    }

    @Test
    @DisplayName("the description is editable and the code is not")
    void onlyTheLabelIsEditable() {
        AadeInvoiceTypeView before = invoiceTypes.requireByCode("8.6");
        String original = before.description();

        AadeInvoiceTypeView renamed = invoiceTypes.describe(before.id(), "Δελτίο Παραγγελίας (edited)");
        assertThat(renamed.description()).isEqualTo("Δελτίο Παραγγελίας (edited)");
        assertThat(renamed.code()).isEqualTo("8.6");
        assertThat(renamed.group()).isEqualTo(before.group());

        assertThat(AadeInvoiceTypeService.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("changecode"));

        // Restored: this class asserts against the shipped seed, and the tests share one
        // non-transactional database.
        invoiceTypes.describe(before.id(), original);
        assertThat(invoiceTypes.requireByCode("8.6").description()).isEqualTo(original);
    }

    @Test
    @DisplayName("a blank description is refused with its reason")
    void blankDescriptionIsRefused() {
        long id = invoiceTypes.requireByCode("8.6").id();

        assertThatExceptionOfType(InvalidAadeInvoiceTypeException.class)
                .isThrownBy(() -> invoiceTypes.describe(id, "  "))
                .withMessageContaining("blank");
    }

    @Test
    @DisplayName("a retired code is deactivated, not deleted")
    void deactivateRetiredCode() {
        AadeInvoiceTypeView type = invoiceTypes.requireByCode("8.5");

        invoiceTypes.deactivate(type.id());
        assertThat(invoiceTypes.require(type.id()).active()).isFalse();
        assertThat(invoiceTypes.active()).extracting(AadeInvoiceTypeView::code)
                .doesNotContain("8.5");
        assertThat(invoiceTypes.all()).extracting(AadeInvoiceTypeView::code).contains("8.5");

        invoiceTypes.reactivate(type.id());
        assertThat(invoiceTypes.require(type.id()).active()).isTrue();
    }

    @Test
    @DisplayName("an unknown code says the list is the specification's, not ours")
    void unknownCode() {
        assertThatExceptionOfType(AadeInvoiceTypeNotFoundException.class)
                .isThrownBy(() -> invoiceTypes.requireByCode("99.9"))
                .withMessageContaining("SimpleTypes-v2.0.1.xsd");

        assertThatExceptionOfType(AadeInvoiceTypeNotFoundException.class)
                .isThrownBy(() -> invoiceTypes.require(999_999L));

        assertThat(invoiceTypes.findByCode("99.9")).isEmpty();
        assertThat(invoiceTypes.findByCode(null)).isEmpty();
        assertThat(invoiceTypes.findByCode("   ")).isEmpty();
    }

    /**
     * The {@code InvoiceType} enumeration, parsed out of the XSD in the repository.
     *
     * <p>⚠️ Read from the <strong>XSD</strong> deliberately, not from the annex PDF. The
     * enumerations are flat {@code <xs:enumeration value="…"/>} elements with no layout at all, so
     * there is nothing for an extractor to misalign — which is the whole reason the XSDs are kept
     * unzipped in the repository. See {@code docs/aade/v2.0.1/README.md}.
     */
    private static List<String> invoiceTypeEnumerationFromTheXsd() throws IOException {
        if (!Files.exists(XSD)) {
            return List.of();
        }
        String xsd = Files.readString(XSD, StandardCharsets.UTF_8);
        Matcher simpleType = Pattern
                .compile("<xs:simpleType name=\"InvoiceType\">(.*?)</xs:simpleType>",
                        Pattern.DOTALL)
                .matcher(xsd);
        if (!simpleType.find()) {
            return List.of();
        }

        Matcher values = Pattern.compile("<xs:enumeration value=\"([^\"]+)\"")
                .matcher(simpleType.group(1));
        return values.results().map(result -> result.group(1)).toList();
    }
}
