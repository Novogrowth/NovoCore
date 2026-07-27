package gr.novotrade.novocore.core.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.tax.InvalidVatExemptionReasonException;
import gr.novotrade.novocore.core.api.tax.NewVatExemptionReason;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The AADE VAT exemption reason lookup — structure, with the real list still to come.
 *
 * <p>These tests use codes in the 9000s so they cannot collide with AADE's real 1–31 range when
 * the verified rows are loaded. That matters more than usual here: the real codes are legally
 * meaningful, and a test fixture squatting on code 6 would have to be untangled from real data.
 */
class VatExemptionReasonIT extends AbstractCoreIntegrationTest {

    @Autowired
    private VatExemptionReasonService reasons;

    @Test
    @DisplayName("the table is deliberately unseeded until the verified AADE list arrives")
    void aadeRangeIsUnseeded() {
        // An empty table is visibly incomplete. A plausible wrong list transcribed from memory
        // would not be, and these codes are transmitted to AADE.
        assertThat(reasons.all())
                .as("nothing in AADE's real 1-31 code range should exist yet")
                .noneMatch(reason -> reason.code() >= 1 && reason.code() <= 31);
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
