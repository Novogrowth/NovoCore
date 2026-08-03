package gr.novotrade.novocore.core.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.codification.StatutoryCodification;
import gr.novotrade.novocore.core.api.tax.InvalidVatExemptionReasonException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonView;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The AADE VAT exemption reason codification, against the real seeded list (V8, completed by V32).
 *
 * <h2>⚠️ This class was rewritten in R1a, and what changed is worth knowing</h2>
 *
 * <p>It used to create its own fixtures in the 9000 code range, because the service had a
 * {@code create} method. <strong>It does not any more.</strong> This is a
 * {@link StatutoryCodification}: rows come from Flyway and from nowhere else, so there is no way to
 * make a fixture and no need for one — the seed <em>is</em> the fixture, and asserting against it
 * is asserting against the thing that ships.
 *
 * <p>Two assertions were deliberately changed rather than preserved, and both were correct when
 * written:
 *
 * <ul>
 *   <li>{@code findByCode(24)} and {@code findByCode(28)} used to assert <strong>empty</strong>,
 *       recording that Prosvasis Go's list has gaps there. It was an honest statement of what was
 *       known and it asked to be revisited against AADE's published table. The artefact answered:
 *       {@code VatExemptionType} in {@code SimpleTypes-v2.0.1.xsd} is {@code xs:int} restricted to
 *       {@code 1..31} with no gaps, and annex 8.3 lists all thirty-one. They were absent from Go,
 *       not retired by AADE.
 *   <li>{@code codesAreImmutable} used to assert that no method on the service contained
 *       {@code "describe"}. Its <em>intent</em> — the code and the myDATA string are not editable —
 *       is right and is asserted more precisely below. Its <em>implementation</em> was broader than
 *       its intent, and a description is a label rather than a tax fact.
 * </ul>
 */
class VatExemptionReasonIT extends AbstractCoreIntegrationTest {

    @Autowired
    private VatExemptionReasonService reasons;

    /**
     * ⚠️ All 31 AADE codes, with no gaps. V8 seeded 29 from Go; V32 added 24 and 28 from annex 8.3.
     */
    private static final List<Integer> SEEDED_CODES =
            IntStream.rangeClosed(1, 31).boxed().toList();

    /**
     * The reasons with no myDATA string at all.
     *
     * <p>29–31 are OSS/IOSS, which Go has no mapping for. ⚠️ <strong>24 and 28 join them, and that
     * was a decision rather than an omission:</strong> annex 8.3 gives the reason <em>text</em>, not
     * a wire string — the {@code N-description} form in the other 26 rows is Go's rendering,
     * transcribed verbatim because composing one is a bet. Codes 12 and 13 are the proof that the
     * bet loses. Go has no row for 24 or 28, so there is nothing verbatim to copy and a composed
     * value would be a fabricated code that gets transmitted.
     */
    private static final List<Integer> CODES_WITHOUT_MYDATA_MAPPING = List.of(24, 28, 29, 30, 31);

    @Test
    @DisplayName("all 31 AADE codes are seeded, with no gaps")
    void aadeListIsSeeded() {
        assertThat(reasons.all()).extracting(VatExemptionReasonView::code)
                .containsExactlyElementsOf(SEEDED_CODES);

        // Stated explicitly, because the previous revision of this test asserted the opposite and
        // was right to at the time. The XSD is what settled it: xs:int restricted to 1..31.
        assertThat(reasons.findByCode(24)).isPresent();
        assertThat(reasons.findByCode(28)).isPresent();

        assertThat(reasons.all()).allSatisfy(reason -> {
            assertThat(reason.active()).isTrue();
            // AADE's "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών" reads Όχι for every entry in this list.
            assertThat(reason.inputVatDeductible()).isFalse();
            // Go repeats the code inside its description text ("1 - Χωρίς ΦΠΑ - ..."); the code is
            // its own column here, so the prefix is stripped rather than stored twice.
            assertThat(reason.description())
                    .as("description should not repeat the code")
                    .doesNotStartWith(reason.code() + " -")
                    .doesNotStartWith(reason.code() + "-");
        });
    }

    @Test
    @DisplayName("Greek and the recodified article numbering survive the seed intact")
    void seededTextIsIntact() {
        // Mojibake here would be silent and would reach AADE. Flyway's encoding is pinned to
        // UTF-8 explicitly (step 3) precisely so this holds on Windows too.
        assertThat(reasons.requireByCode(1).description())
                .isEqualTo("Χωρίς ΦΠΑ - άρθρο 2 και 3 του Κώδικα ΦΠΑ");
        assertThat(reasons.requireByCode(20).description())
                .as("\"ΦΠΑ εμπεριεχόμενος\" is a different thing from \"Χωρίς ΦΠΑ\" — VAT included "
                        + "in the price rather than absent — so the distinction must survive")
                .isEqualTo("ΦΠΑ εμπεριεχόμενος - άρθρο 50 του Κώδικα ΦΠΑ");
        assertThat(reasons.requireByCode(31).description())
                .isEqualTo("Χωρίς ΦΠΑ - άρθρο 58 του Κώδικα ΦΠΑ (IOSS)");
    }

    @Test
    @DisplayName("codes 24 and 28 carry annex 8.3's ν.5144/2024 text, read from a rasterised page")
    void artefactSourcedCodesAreIntact() {
        // ⚠️ Read VISUALLY from a 170 dpi render of page 95, never from `pdftotext` — that
        // extractor drifts the code and description columns apart on these tables and produces
        // clean, plausible, off-by-one pairs. A named anti-pattern in CLAUDE.md.
        assertThat(reasons.requireByCode(24).description())
                .isEqualTo("Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ");
        assertThat(reasons.requireByCode(28).description())
                .isEqualTo("Χωρίς ΦΠΑ - άρθρο 29 περ. β' παρ.1 του Κώδικα ΦΠΑ, (Tax Free)");

        // The normalisation, asserted rather than left in a migration comment: the annex renders
        // 28 with a typographic en dash and right single quote. Both become ASCII, which is the
        // punctuation all 29 pre-existing rows already use — the comparison that called those "an
        // exact match" against AADE was already a normalised one.
        assertThat(reasons.requireByCode(28).description())
                .doesNotContain("–")
                .doesNotContain("’");
    }

    @Test
    @DisplayName("storing the myDATA string verbatim was load-bearing: codes 12 and 13 differ")
    void verbatimMydataCodeIsJustified() {
        // V5 stored mydata_code separately rather than composing it from code and description, and
        // recorded that a test should check whether the composition holds once the real rows
        // landed. It does not — for exactly two rows, whose Go description names
        // "Πλοία Ανοικτής Θαλάσσης" while their myDATA string does not. Had the value been
        // composed, those two would have been transmitted wrong.
        //
        // ⚠️ This is also the whole argument for 24 and 28 carrying NULL rather than a composed
        // string. The composition is observably not a rule.
        assertThat(reasons.all())
                .filteredOn(reason -> reason.mydataCodeIfAny().isPresent()
                        && !reason.mydataCodeMatchesDescription())
                .extracting(VatExemptionReasonView::code)
                .containsExactly(12, 13);

        assertThat(reasons.requireByCode(12).description()).contains("Πλοία Ανοικτής Θαλάσσης");
        assertThat(reasons.requireByCode(12).requireMydataCode())
                .isEqualTo("12-Χωρίς ΦΠΑ - άρθρο 32 του Κώδικα ΦΠΑ")
                .doesNotContain("Πλοία");

        assertThat(reasons.all())
                .filteredOn(reason -> reason.mydataCodeIfAny().isPresent()
                        && reason.code() != 12 && reason.code() != 13)
                .allSatisfy(reason ->
                        assertThat(reason.mydataCodeMatchesDescription()).isTrue());
    }

    @Test
    @DisplayName("five reasons have no myDATA code, and refuse to invent one")
    void reasonsWithoutAMydataMappingRefuseToComposeOne() {
        assertThat(reasons.all())
                .filteredOn(reason -> reason.mydataCodeIfAny().isEmpty())
                .extracting(VatExemptionReasonView::code)
                .containsExactlyElementsOf(CODES_WITHOUT_MYDATA_MAPPING);

        VatExemptionReasonView ioss = reasons.requireByCode(31);
        assertThat(ioss.mydataCodeMatchesDescription())
                .as("absence is not a match")
                .isFalse();

        // The point of the distinction. A transmitting caller must fail here, naming the reason,
        // rather than sending a blank or a value composed on the spot — phase 7's obligation.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(ioss::requireMydataCode)
                .withMessageContaining("31")
                .withMessageContaining("cannot be transmitted");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(reasons.requireByCode(24)::requireMydataCode)
                .withMessageContaining("24");
    }

    @Test
    @DisplayName("⚠️ there is no way to author a reason — the contract forbids it")
    void thereIsNoCreatePath() {
        // Q1-b, closed by consequence rather than by a judgement call about usage counts. These
        // codes are transmitted to the tax authority; a row somebody typed into a form is a
        // compliance defect rather than a data-entry mistake, so a new code is a migration with the
        // artefact it was read from sitting beside it.
        //
        // StatutoryCodificationRulesTest makes this a build failure across every implementor. It is
        // restated here because this is the service the rule was written for, and a reader of this
        // class should not have to find an architecture test to learn that create is gone.
        assertThat(VatExemptionReasonService.class.getMethods())
                .as("no create path on a statutory codification")
                .noneMatch(method -> method.getName().toLowerCase().startsWith("create"));

        assertThat(StatutoryCodification.class)
                .isAssignableFrom(VatExemptionReasonService.class);
    }

    @Test
    @DisplayName("the description is editable; the code and the myDATA string are not")
    void onlyTheLabelIsEditable() {
        // ⚠️ This replaces an assertion that forbade `describe` outright. The intent was that a
        // typo corrected in place must not leave already-issued documents referencing something
        // else — which is true of the CODE and of the MYDATA STRING, the value that goes on the
        // wire. A description is a label, and correcting one changes nothing about what was
        // declared.
        assertThat(VatExemptionReasonService.class.getMethods())
                .noneMatch(method -> {
                    String name = method.getName().toLowerCase();
                    return name.startsWith("set") || name.contains("changecode")
                            || name.contains("mydata") && !name.startsWith("find")
                                    && !name.startsWith("require");
                });

        VatExemptionReasonView before = reasons.requireByCode(27);
        String original = before.description();

        VatExemptionReasonView renamed = reasons.describe(before.id(), "Λοιπές Εξαιρέσεις ΦΠΑ (edited)");
        assertThat(renamed.description()).isEqualTo("Λοιπές Εξαιρέσεις ΦΠΑ (edited)");
        assertThat(renamed.code()).isEqualTo(27);
        assertThat(renamed.mydataCode())
                .as("the wire string is untouched by a label correction")
                .isEqualTo(before.mydataCode());

        // Restored, because this class asserts against the shipped seed and the tests share one
        // non-transactional database — a fixture left behind would break a neighbour.
        reasons.describe(before.id(), original);
        assertThat(reasons.requireByCode(27).description()).isEqualTo(original);
    }

    @Test
    @DisplayName("a blank description is refused with its reason, not swallowed")
    void blankDescriptionIsRefused() {
        long id = reasons.requireByCode(27).id();

        assertThatExceptionOfType(InvalidVatExemptionReasonException.class)
                .isThrownBy(() -> reasons.describe(id, "   "))
                .withMessageContaining("blank");
    }

    @Test
    @DisplayName("a retired reason is deactivated, not deleted")
    void deactivateRetiredReason() {
        VatExemptionReasonView reason = reasons.requireByCode(26);

        reasons.deactivate(reason.id());
        assertThat(reasons.require(reason.id()).active()).isFalse();
        assertThat(reasons.active()).extracting(VatExemptionReasonView::code).doesNotContain(26);
        // Still readable: documents already issued under it must remain explicable.
        assertThat(reasons.all()).extracting(VatExemptionReasonView::code).contains(26);

        reasons.reactivate(reason.id());
        assertThat(reasons.require(reason.id()).active()).isTrue();
    }

    @Test
    @DisplayName("a missing reason explains that code gaps are expected")
    void missingReason() {
        assertThatExceptionOfType(VatExemptionReasonNotFoundException.class)
                .isThrownBy(() -> reasons.requireByCode(9999))
                .withMessageContaining("retired");

        assertThatExceptionOfType(VatExemptionReasonNotFoundException.class)
                .isThrownBy(() -> reasons.require(999_999L))
                .withMessageContaining("999999");

        assertThat(reasons.findByCode(9998)).isEmpty();
    }

    @Test
    @DisplayName("reasons are returned in AADE code order")
    void orderedByCode() {
        // The reason the code is an integer rather than text: as text, "10" would sort before "2"
        // and a picker of 31 entries would be in a nonsensical order.
        assertThat(reasons.all()).extracting(VatExemptionReasonView::code).isSorted();
    }
}
