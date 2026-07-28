package gr.novotrade.novocore.core.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.tax.InvalidVatExemptionReasonException;
import gr.novotrade.novocore.core.api.tax.NewVatExemptionReason;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The AADE VAT exemption reason lookup, against the real seeded list (V8).
 *
 * <p>These tests use codes in the 9000s so they cannot collide with AADE's real 1–31 range, which
 * is now populated. That mattered more than usual here: the real codes are legally meaningful, and
 * a fixture squatting on code 6 would have had to be untangled from real data.
 *
 * <p>The seed assertions are scoped to codes 1–31 for the same reason the chart-of-accounts tests
 * scope theirs to the seeded groups — these tests share one non-transactional database, so a
 * fixture created by a neighbouring test must not be able to break a count.
 */
class VatExemptionReasonIT extends AbstractCoreIntegrationTest {

    @Autowired
    private VatExemptionReasonService reasons;

    /** AADE codes V8 seeds: 1–31 with 24 and 28 absent from Prosvasis Go's list. */
    private static final List<Integer> SEEDED_CODES = List.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 25, 26, 27, 29, 30, 31);

    /** The three OSS/IOSS reasons Go has no myDATA mapping for. */
    private static final List<Integer> CODES_WITHOUT_MYDATA_MAPPING = List.of(29, 30, 31);

    @Test
    @DisplayName("the verified AADE list is seeded, with the gaps Go's list actually has")
    void aadeListIsSeeded() {
        assertThat(seeded()).extracting(VatExemptionReasonView::code)
                .containsExactlyElementsOf(SEEDED_CODES);

        // Stated as an assertion rather than left implicit: 24 and 28 are missing from Go's list,
        // not known to be retired by AADE. If AADE defines them and we need them, that is two
        // INSERTs — but nothing should quietly assume the range is contiguous in the meantime.
        assertThat(reasons.findByCode(24)).isEmpty();
        assertThat(reasons.findByCode(28)).isEmpty();

        assertThat(seeded()).allSatisfy(reason -> {
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
    @DisplayName("storing the myDATA string verbatim was load-bearing: codes 12 and 13 differ")
    void verbatimMydataCodeIsJustified() {
        // V5 stored mydata_code separately rather than composing it from code and description, and
        // recorded that a test should check whether the composition holds once the real rows
        // landed. It does not — for exactly two rows, whose Go description names
        // "Πλοία Ανοικτής Θαλάσσης" while their myDATA string does not. Had the value been
        // composed, those two would have been transmitted wrong.
        assertThat(seeded())
                .filteredOn(reason -> reason.mydataCodeIfAny().isPresent()
                        && !reason.mydataCodeMatchesDescription())
                .extracting(VatExemptionReasonView::code)
                .containsExactly(12, 13);

        assertThat(reasons.requireByCode(12).description()).contains("Πλοία Ανοικτής Θαλάσσης");
        assertThat(reasons.requireByCode(12).requireMydataCode())
                .isEqualTo("12-Χωρίς ΦΠΑ - άρθρο 32 του Κώδικα ΦΠΑ")
                .doesNotContain("Πλοία");

        // Every other mapped row does compose cleanly, which is what makes the two exceptions
        // worth naming rather than a general disclaimer.
        assertThat(seeded())
                .filteredOn(reason -> reason.mydataCodeIfAny().isPresent()
                        && reason.code() != 12 && reason.code() != 13)
                .allSatisfy(reason ->
                        assertThat(reason.mydataCodeMatchesDescription()).isTrue());
    }

    @Test
    @DisplayName("the OSS and IOSS reasons have no myDATA code, and refuse to invent one")
    void ossAndIossHaveNoMydataMapping() {
        assertThat(seeded())
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
    }

    @Test
    @DisplayName("a reason may be created with no myDATA code, but never with a blank one")
    void blankMydataCodeIsNormalisedToAbsent() {
        // Blank and null would otherwise be two representations of the same state, and the CHECK
        // constraint refuses '' outright.
        VatExemptionReasonView unmapped = reasons.create(
                NewVatExemptionReason.withoutMydataCode(9030, "Unmapped reason (test)", false));
        assertThat(unmapped.mydataCodeIfAny()).isEmpty();

        VatExemptionReasonView blank = reasons.create(new NewVatExemptionReason(
                9031, "Blank myDATA code (test)", "   ", false));
        assertThat(blank.mydataCodeIfAny())
                .as("blank normalised to absent rather than stored")
                .isEmpty();

        // Two unmapped reasons do not collide: absence is not a duplicate myDATA string.
        assertThat(reasons.requireByCode(9030).mydataCodeIfAny()).isEmpty();
        assertThat(reasons.requireByCode(9031).mydataCodeIfAny()).isEmpty();
    }

    /** The rows V8 seeded, i.e. excluding the 9000-range fixtures these tests create. */
    private List<VatExemptionReasonView> seeded() {
        return reasons.all().stream()
                .filter(reason -> reason.code() >= 1 && reason.code() <= 31)
                .toList();
    }

    @Test
    @DisplayName("a reason round-trips with its myDATA string stored verbatim")
    void createAndRead() {
        // Shaped exactly like a real entry so the structure is exercised against realistic data:
        // AADE code 6 is "Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ", with no input VAT deduction right.
        VatExemptionReasonView created = reasons.create(new NewVatExemptionReason(
                9006,
                "Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ (test)",
                "9006-Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ (test)",
                false));

        assertThat(created.code()).isEqualTo(9006);
        assertThat(created.inputVatDeductible())
                .as("AADE's \"Δικαίωμα έκπτωσης Φ.Π.Α. εισροών\" is Όχι for every entry seen so far")
                .isFalse();
        assertThat(created.active()).isTrue();

        // Greek survives the round trip, as it must for a value that goes on the wire.
        VatExemptionReasonView read = reasons.requireByCode(9006);
        assertThat(read.description()).isEqualTo("Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ (test)");
        assertThat(read.mydataCode())
                .isEqualTo("9006-Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ (test)");
        assertThat(read.mydataCodeMatchesDescription())
                .as("this row was built as code-description, so the composition holds here; "
                        + "whether it holds for all ~29 real rows is what the real seed will show")
                .isTrue();
    }

    @Test
    @DisplayName("the input-VAT-deduction flag can be true, so it is not dead weight")
    void inputVatDeductibleCanBeTrue() {
        // Every AADE entry seen so far is "Όχι", which would make the column look redundant.
        // It is a genuine per-reason distinction in AADE's table, so the column must be able to
        // carry both values rather than being a constant waiting to be optimised away.
        VatExemptionReasonView deductible = reasons.create(new NewVatExemptionReason(
                9099, "Test reason with deduction right", "9099-Test with deduction right", true));

        assertThat(deductible.inputVatDeductible()).isTrue();
        assertThat(reasons.requireByCode(9099).inputVatDeductible()).isTrue();
    }

    @Test
    @DisplayName("a duplicate AADE code is refused")
    void duplicateCodeIsRefused() {
        reasons.create(new NewVatExemptionReason(
                9010, "First", "9010-First", false));

        assertThatExceptionOfType(InvalidVatExemptionReasonException.class)
                .isThrownBy(() -> reasons.create(new NewVatExemptionReason(
                        9010, "Second", "9010-Second", false)))
                .withMessageContaining("9010");
    }

    @Test
    @DisplayName("a duplicate myDATA string is refused, since that is what is transmitted")
    void duplicateMydataCodeIsRefused() {
        reasons.create(new NewVatExemptionReason(
                9011, "Shared string", "9011-Shared myDATA string", false));

        assertThatExceptionOfType(InvalidVatExemptionReasonException.class)
                .isThrownBy(() -> reasons.create(new NewVatExemptionReason(
                        9012, "Different code", "9011-Shared myDATA string", false)))
                .withMessageContaining("already exists");
    }

    @Test
    @DisplayName("a non-positive code is refused")
    void nonPositiveCodeIsRefused() {
        assertThatExceptionOfType(InvalidVatExemptionReasonException.class)
                .isThrownBy(() -> reasons.create(new NewVatExemptionReason(
                        0, "Zero code", "0-Zero code", false)))
                .withMessageContaining("positive integers");
    }

    @Test
    @DisplayName("a retired reason is deactivated, not deleted or edited")
    void deactivateRetiredReason() {
        VatExemptionReasonView retired = reasons.create(new NewVatExemptionReason(
                9020, "Retired reason", "9020-Retired reason", false));

        reasons.deactivate(retired.id());

        assertThat(reasons.require(retired.id()).active()).isFalse();
        assertThat(reasons.active()).extracting(VatExemptionReasonView::code)
                .doesNotContain(9020);
        // Still readable: documents already issued under it must remain explicable.
        assertThat(reasons.all()).extracting(VatExemptionReasonView::code).contains(9020);

        reasons.reactivate(retired.id());
        assertThat(reasons.require(retired.id()).active()).isTrue();
    }

    @Test
    @DisplayName("neither the code nor the myDATA string is editable")
    void codesAreImmutable() {
        // A typo corrected in place would leave already-issued documents referencing something
        // else. There is deliberately no mutator for either value.
        assertThat(VatExemptionReasonService.class.getMethods())
                .noneMatch(method -> {
                    String name = method.getName().toLowerCase();
                    return name.startsWith("set") || name.contains("changecode")
                            || name.contains("rename") || name.contains("describe");
                });
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
    @DisplayName("reasons are returned in AADE code order, not insertion order")
    void orderedByCode() {
        reasons.create(new NewVatExemptionReason(9042, "Later", "9042-Later", false));
        reasons.create(new NewVatExemptionReason(9041, "Earlier", "9041-Earlier", false));

        // The reason the code is an integer rather than text: as text, "9410" would sort before
        // "942" and a picker of ~29 entries would be in a nonsensical order.
        assertThat(reasons.all()).extracting(VatExemptionReasonView::code).isSorted();
    }
}
