package gr.novotrade.novocore.core.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.tax.InvalidVatClassException;
import gr.novotrade.novocore.core.api.tax.NewVatClass;
import gr.novotrade.novocore.core.api.tax.VatClassNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The VAT class lookup and its real seeded rate list.
 *
 * <p>As in {@code ChartOfAccountsIT}, tests that add classes use codes prefixed {@code TEST-} so
 * seed assertions can exclude them and stay independent of test ordering.
 */
class VatClassIT extends AbstractCoreIntegrationTest {

    /** The nine codes V5 seeds, in the order the service returns them (rate, then code). */
    private static final List<String> SEEDED_CODES =
            List.of("0", "1030", "1040", "1041", "1060", "1091", "1131", "1170", "1410");

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // The seed
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("all nine Prosvasis Go rate classes are seeded, with the right rates")
    void seededRates() {
        // Ordered by rate then code, which is the order a rate picker should offer.
        assertThat(seeded()).extracting(VatClassView::code)
                .containsExactlyElementsOf(SEEDED_CODES);

        // Compared by value, not by scale: the column is numeric(19,6) so every rate reads back
        // as 24.000000, and BigDecimal.equals would compare the scale as well as the value.
        assertRate("0", "0");
        assertRate("1030", "3");
        assertRate("1040", "4");
        assertRate("1041", "4");
        assertRate("1060", "6");
        assertRate("1091", "9");
        assertRate("1131", "13");
        assertRate("1170", "17");
        assertRate("1410", "24");
    }

    @Test
    @DisplayName("the Greek descriptions survive the migration intact, not as mojibake")
    void greekDescriptionsAreIntact() {
        // The reason spring.flyway.encoding is stated explicitly. If the migration were applied
        // under a platform default encoding on Windows, these would arrive mangled and Flyway's
        // checksum would then differ between environments.
        assertThat(vatClasses.requireByCode("1410").description()).isEqualTo("ΦΠΑ 24%");
        assertThat(vatClasses.requireByCode("0").description())
                .isEqualTo("Μηδενικός Συντελεστής ΦΠΑ 0%");
        assertThat(vatClasses.requireByCode("1030").description())
                .isEqualTo("ΦΠΑ 3% (αρ.31 ν.5057/2023)");
        assertThat(vatClasses.requireByCode("1041").description())
                .isEqualTo("ΦΠΑ 4% (αρ.31 ν.5057/2023)");
    }

    @Test
    @DisplayName("nine classes but only eight distinct rates — 4% appears twice")
    void fourPercentAppearsTwice() {
        // The fact that makes the code, not the rate, the identity.
        assertThat(seeded()).hasSize(9);

        long distinctRates = seeded().stream()
                .map(view -> view.ratePercent().stripTrailingZeros())
                .distinct()
                .count();
        assertThat(distinctRates)
                .as("nine classes but eight distinct percentages")
                .isEqualTo(8);

        List<VatClassView> fourPercent = seeded().stream()
                .filter(view -> view.ratePercent().compareTo(new BigDecimal("4")) == 0)
                .toList();

        assertThat(fourPercent)
                .as("1040 is a rate in its own right; 1041 is the island-reduced counterpart of "
                        + "6% under αρ.31 ν.5057/2023. Same percentage, different legal basis.")
                .extracting(VatClassView::code)
                .containsExactly("1040", "1041");
    }

    @Test
    @DisplayName("the island-reduced mappings run mainland to reduced, one level deep")
    void islandReducedMappings() {
        assertReducedCounterpart("1410", "1170");
        assertReducedCounterpart("1131", "1091");
        assertReducedCounterpart("1060", "1041");
        assertReducedCounterpart("1040", "1030");

        // The reduced classes have no counterpart of their own — mappings are not a chain.
        assertThat(vatClasses.requireByCode("1170").hasReducedCounterpart()).isFalse();
        assertThat(vatClasses.requireByCode("1091").hasReducedCounterpart()).isFalse();
        assertThat(vatClasses.requireByCode("1041").hasReducedCounterpart()).isFalse();
        assertThat(vatClasses.requireByCode("1030").hasReducedCounterpart()).isFalse();

        // Nothing below zero to reduce 0% to.
        assertThat(vatClasses.requireByCode("0").hasReducedCounterpart()).isFalse();
    }

    @Test
    @DisplayName("exactly four classes are island-reduced counterparts")
    void reducedCounterpartsList() {
        assertThat(vatClasses.reducedCounterparts())
                .extracting(VatClassView::code)
                .containsExactlyInAnyOrder("1030", "1041", "1091", "1170");
    }

    @Test
    @DisplayName("every seeded class is active and computes VAT correctly")
    void seededClassesAreUsable() {
        assertThat(seeded()).allSatisfy(view -> assertThat(view.active()).isTrue());

        assertThat(vatClasses.requireByCode("1410")
                .vatOn(Money.ofEur("100.00"), RoundingMode.HALF_UP))
                .isEqualTo(Money.ofEur("24.00"));
        assertThat(vatClasses.requireByCode("1170")
                .vatOn(Money.ofEur("100.00"), RoundingMode.HALF_UP))
                .isEqualTo(Money.ofEur("17.00"));
        assertThat(vatClasses.requireByCode("0")
                .vatOn(Money.ofEur("100.00"), RoundingMode.HALF_UP))
                .isEqualTo(Money.ofEur("0.00"));
    }

    @Test
    @DisplayName("the rate column is numeric(19,6), the multiplier precision class")
    void rateColumnScale() {
        assertThat(jdbc.queryForObject("""
                SELECT numeric_scale FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'vat_class' AND column_name = 'rate_percent'
                """, Integer.class))
                .isEqualTo(VatClassView.RATE_SCALE);
    }

    // ---------------------------------------------------------------------------------------
    // Runtime editability — the reason this is a table and not an enum
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a new rate can be added at runtime without a migration")
    void createNewRate() {
        VatClassView created = vatClasses.create(
                new NewVatClass("TEST-1100", "ΦΠΑ 11% (test)", new BigDecimal("11")));

        assertThat(created.code()).isEqualTo("TEST-1100");
        assertThat(created.ratePercent()).isEqualByComparingTo("11");
        assertThat(created.active()).isTrue();
        assertThat(created.hasReducedCounterpart()).isFalse();

        assertThat(vatClasses.active()).extracting(VatClassView::code).contains("TEST-1100");

        List<AuditEntry> entries =
                auditLog.findForEntity("VatClass", String.valueOf(created.id()), 5);
        assertThat(entries).isNotEmpty();
        assertThat(entries.getFirst().action()).isEqualTo("vat-class.created");
        assertThat(entries.getFirst().detail()).containsEntry("code", "TEST-1100");
    }

    @Test
    @DisplayName("a duplicate code is refused; two classes may share a rate but never a code")
    void duplicateCodeIsRefused() {
        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.create(
                        new NewVatClass("1410", "Duplicate", new BigDecimal("24"))))
                .withMessageContaining("already exists");
    }

    @Test
    @DisplayName("a rate outside 0 or 1-100 is refused, naming the factor-of-100 mistake")
    void rateOutsideRangeIsRefused() {
        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.create(
                        new NewVatClass("TEST-BAD-HIGH", "Too high", new BigDecimal("101"))))
                .withMessageContaining("exactly 0, or between 1 and 100");

        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.create(
                        new NewVatClass("TEST-BAD-NEG", "Negative", new BigDecimal("-1"))));

        // The case a plain 0-100 bound let through: 0.24 written for 24%, which was accepted as a
        // quarter of one percent and undercharged by the exact factor V5's comment claimed to
        // prevent. An undercharge is not recoverable from the customer once the invoice is issued.
        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.create(
                        new NewVatClass("TEST-BAD-FRAC", "Fraction", new BigDecimal("0.24"))))
                .withMessageContaining("undercharge by a factor of 100");

        // Zero remains valid: the zero-rated class is real and distinct from an exempt line, which
        // is why the rule is "exactly 0 or at least 1" rather than a flat minimum.
        assertThat(vatClasses.create(
                new NewVatClass("TEST-OK-ZERO", "Zero rate (test)", new BigDecimal("0")))
                .isZeroRated()).isTrue();
    }

    @Test
    @DisplayName("there is no way to change a rate in place")
    void ratesAreNotEditable() {
        // Asserted as an interface property rather than a behaviour: editing a rate would
        // retroactively change what every invoice issued under that class appears to have
        // charged. A rate change is a new class plus deactivation of the old one.
        assertThat(VatClassService.class.getMethods())
                .as("no method on VatClassService may set a rate")
                .noneMatch(method -> method.getName().toLowerCase().contains("rate"));
    }

    @Test
    @DisplayName("a rate change is modelled as a new class plus deactivation of the old one")
    void rateChangeBySupersession() {
        VatClassView old = vatClasses.create(
                new NewVatClass("TEST-OLD", "Superseded rate", new BigDecimal("20")));
        VatClassView replacement = vatClasses.create(
                new NewVatClass("TEST-NEW", "Replacement rate", new BigDecimal("21")));

        vatClasses.deactivate(old.id());

        assertThat(vatClasses.require(old.id()).active()).isFalse();
        assertThat(vatClasses.active()).extracting(VatClassView::code)
                .doesNotContain("TEST-OLD")
                .contains("TEST-NEW");
        // Still readable — historical invoices referencing it must remain explicable.
        assertThat(vatClasses.all()).extracting(VatClassView::code).contains("TEST-OLD");

        vatClasses.reactivate(old.id());
        assertThat(vatClasses.require(old.id()).active()).isTrue();
        assertThat(replacement.active()).isTrue();
    }

    // ---------------------------------------------------------------------------------------
    // Island-reduced mapping rules
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a mapping can be set and cleared, and is audited")
    void mapAndClearCounterpart() {
        VatClassView mainland = vatClasses.create(
                new NewVatClass("TEST-MAP-30", "Mainland 30 (test)", new BigDecimal("30")));
        VatClassView reduced = vatClasses.create(
                new NewVatClass("TEST-MAP-20", "Reduced 20 (test)", new BigDecimal("20")));

        VatClassView mapped = vatClasses.mapToReducedCounterpart(mainland.id(), reduced.id());
        assertThat(mapped.reducedCounterpart()).contains(reduced.id());

        assertThat(auditLog.findForEntity("VatClass", String.valueOf(mainland.id()), 5))
                .extracting(AuditEntry::action)
                .contains("vat-class.reduced-counterpart-set");

        assertThat(vatClasses.clearReducedCounterpart(mainland.id()).hasReducedCounterpart())
                .isFalse();
        // Both classes survive the mapping being removed.
        assertThat(vatClasses.find(reduced.id())).isPresent();
    }

    @Test
    @DisplayName("a counterpart must be rated lower than the class it reduces")
    void counterpartMustBeLower() {
        VatClassView lower = vatClasses.create(
                new NewVatClass("TEST-LOW", "Low (test)", new BigDecimal("5")));
        VatClassView higher = vatClasses.create(
                new NewVatClass("TEST-HIGH", "High (test)", new BigDecimal("15")));

        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.mapToReducedCounterpart(lower.id(), higher.id()))
                .withMessageContaining("not lower");

        // Equal is refused too: the mapping means "the reduced rate for this one".
        VatClassView sameRate = vatClasses.create(
                new NewVatClass("TEST-SAME", "Same rate (test)", new BigDecimal("5")));
        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.mapToReducedCounterpart(lower.id(), sameRate.id()));
    }

    @Test
    @DisplayName("a class cannot be its own counterpart")
    void noSelfMapping() {
        VatClassView self = vatClasses.create(
                new NewVatClass("TEST-SELF", "Self (test)", new BigDecimal("7")));

        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.mapToReducedCounterpart(self.id(), self.id()))
                .withMessageContaining("its own island-reduced counterpart");
    }

    @Test
    @DisplayName("mappings are one level deep, not a chain")
    void noChains() {
        VatClassView top = vatClasses.create(
                new NewVatClass("TEST-CHAIN-A", "Chain A", new BigDecimal("40")));
        VatClassView middle = vatClasses.create(
                new NewVatClass("TEST-CHAIN-B", "Chain B", new BigDecimal("35")));
        VatClassView bottom = vatClasses.create(
                new NewVatClass("TEST-CHAIN-C", "Chain C", new BigDecimal("30")));

        vatClasses.mapToReducedCounterpart(middle.id(), bottom.id());

        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() -> vatClasses.mapToReducedCounterpart(top.id(), middle.id()))
                .withMessageContaining("one level deep");
    }

    @Test
    @DisplayName("a reduced class cannot be claimed by two mainland classes")
    void counterpartIsOneToOne() {
        VatClassView firstMainland = vatClasses.create(
                new NewVatClass("TEST-CLAIM-A", "Claim A", new BigDecimal("45")));
        VatClassView secondMainland = vatClasses.create(
                new NewVatClass("TEST-CLAIM-B", "Claim B", new BigDecimal("44")));
        VatClassView shared = vatClasses.create(
                new NewVatClass("TEST-CLAIM-C", "Claim C", new BigDecimal("22")));

        vatClasses.mapToReducedCounterpart(firstMainland.id(), shared.id());

        assertThatExceptionOfType(InvalidVatClassException.class)
                .isThrownBy(() ->
                        vatClasses.mapToReducedCounterpart(secondMainland.id(), shared.id()))
                .withMessageContaining("already the reduced counterpart");

        // Re-mapping to the same counterpart is a no-op rather than a failure.
        assertThat(vatClasses.mapToReducedCounterpart(firstMainland.id(), shared.id())
                .reducedCounterpart()).contains(shared.id());
    }

    // ---------------------------------------------------------------------------------------
    // Database-level guarantees
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the database refuses an impossible rate, including a fraction, via raw SQL")
    void databaseRefusesImpossibleRate() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-HIGH", "Probe", new BigDecimal("100.000001")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vat_class_rate_is_a_percentage");

        // V10's lower bound, in the database and not only in Java, so it holds against a psql
        // session and a future migration too.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-FRAC", "Probe fraction", new BigDecimal("0.240000")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vat_class_rate_is_a_percentage");

        // And zero still passes it, which is the half of the rule that had to be preserved.
        jdbc.update("INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-ZERO", "Probe zero", new BigDecimal("0.000000"));
    }

    @Test
    @DisplayName("the database refuses a self-referencing counterpart")
    void databaseRefusesSelfReference() {
        jdbc.update("INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-SELF", "Probe self", new BigDecimal("8"));

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE vat_class SET reduced_counterpart_id = id WHERE code = ?
                """, "TEST-RAW-SELF"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vat_class_not_own_counterpart");
    }

    @Test
    @DisplayName("the database refuses two classes claiming one reduced counterpart")
    void databaseEnforcesOneToOne() {
        jdbc.update("INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-M1", "Probe m1", new BigDecimal("50"));
        jdbc.update("INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-M2", "Probe m2", new BigDecimal("49"));
        jdbc.update("INSERT INTO vat_class (code, description, rate_percent) VALUES (?, ?, ?)",
                "TEST-RAW-R", "Probe r", new BigDecimal("25"));

        jdbc.update("""
                UPDATE vat_class SET reduced_counterpart_id =
                    (SELECT id FROM vat_class WHERE code = 'TEST-RAW-R')
                WHERE code = 'TEST-RAW-M1'
                """);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE vat_class SET reduced_counterpart_id =
                    (SELECT id FROM vat_class WHERE code = 'TEST-RAW-R')
                WHERE code = 'TEST-RAW-M2'
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vat_class_reduced_counterpart_unique");
    }

    @Test
    @DisplayName("a missing class names what was looked for")
    void missingClass() {
        assertThatExceptionOfType(VatClassNotFoundException.class)
                .isThrownBy(() -> vatClasses.require(999_999L))
                .withMessageContaining("999999");

        assertThatExceptionOfType(VatClassNotFoundException.class)
                .isThrownBy(() -> vatClasses.requireByCode("NOPE"))
                .withMessageContaining("NOPE");

        assertThat(vatClasses.findByCode("NOPE")).isEmpty();
    }

    @Test
    @DisplayName("no lookup by rate exists, because a rate does not identify a class")
    void noLookupByRate() {
        assertThat(VatClassService.class.getMethods())
                .as("1040 and 1041 both charge 4%, so findByRate would be right most of the time")
                .noneMatch(method -> method.getName().toLowerCase().contains("byrate"));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private List<VatClassView> seeded() {
        return vatClasses.all().stream()
                .filter(view -> SEEDED_CODES.contains(view.code()))
                .toList();
    }

    private void assertRate(String code, String expectedPercent) {
        assertThat(vatClasses.requireByCode(code).ratePercent())
                .as("rate of VAT class %s", code)
                .isEqualByComparingTo(expectedPercent);
    }

    private void assertReducedCounterpart(String mainlandCode, String reducedCode) {
        VatClassView mainland = vatClasses.requireByCode(mainlandCode);
        VatClassView reduced = vatClasses.requireByCode(reducedCode);

        assertThat(mainland.reducedCounterpart())
                .as("%s should reduce to %s", mainlandCode, reducedCode)
                .contains(reduced.id());
        assertThat(reduced.ratePercent())
                .as("a reduced counterpart must be rated lower")
                .isLessThan(mainland.ratePercent());
    }
}
