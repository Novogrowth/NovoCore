package gr.novotrade.novocore.core.api.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The myDATA payment-method codes on {@link SettlementMethod} — annex 8.12, R1a.
 *
 * <p>No database and no Spring: the mapping is a constructor argument on an enum, deliberately, and
 * needs neither. {@code SettlementMethod} is an enum rather than a table because every value carries
 * behaviour only NovoCore can supply — the cash limit, whether an invoice is born settled — so a row
 * an operator added at runtime would be storable and unhandled. A lookup table would have relocated
 * that problem, not solved it.
 */
class SettlementMethodMydataCodeTest {

    /**
     * Annex 8.12's eight statutory values, read from a rasterised page 105:
     *
     * <pre>
     *   1 Επαγ. Λογαριασμός Πληρωμών Ημεδαπής   5 Επί Πιστώσει
     *   2 Επαγ. Λογαριασμός Πληρωμών Αλλοδαπής  6 Web Banking
     *   3 Μετρητά                               7 POS / e-POS
     *   4 Επιταγή                               8 Άμεσες Πληρωμές IRIS
     * </pre>
     */
    private static final Map<SettlementMethod, Integer> EXPECTED = new EnumMap<>(Map.of(
            SettlementMethod.CASH, 3,
            SettlementMethod.BANK_DEPOSIT, 1,
            SettlementMethod.CARD_POS, 7,
            SettlementMethod.ON_ACCOUNT, 5,
            SettlementMethod.SKROUTZ, 5));

    @Test
    @DisplayName("the five mapped methods carry annex 8.12's code")
    void mappedMethodsCarryTheirCode() {
        EXPECTED.forEach((method, code) ->
                assertThat(method.mydataPaymentCode())
                        .as("%s", method)
                        .contains(code));
    }

    @Test
    @DisplayName("⚠️ THREE methods have no code, not one — and that is a finding about AADE's list")
    void threeMethodsHaveNoCode() {
        // The backend queue recorded this as "one settlement method (ACS) lacks a myDATA code".
        // Three do. PayPal and Stripe were not mentioned at all, and they map to nothing in an
        // eight-value list that knows about domestic and foreign professional payment accounts,
        // cash, cheque, credit, web banking, POS and IRIS — and about none of a courier's
        // cash-on-delivery, PayPal or Stripe.
        //
        // Asserted by NAME rather than by count, which is 8a's gate-3 lesson: a count says "three
        // of something" and cannot say WHICH three came back.
        assertThat(java.util.Arrays.stream(SettlementMethod.values())
                .filter(method -> method.mydataPaymentCode().isEmpty())
                .toList())
                .containsExactlyInAnyOrder(
                        SettlementMethod.ACS_COD,
                        SettlementMethod.PAYPAL,
                        SettlementMethod.STRIPE);
    }

    @Test
    @DisplayName("every value is either mapped or listed open — none is silently absent")
    void everyValueIsAccountedFor() {
        // The negative control for the two tests above: without it, adding a ninth settlement
        // method would leave it unmapped and unlisted, and both tests would still pass.
        for (SettlementMethod method : SettlementMethod.values()) {
            boolean mapped = EXPECTED.containsKey(method);
            assertThat(method.mydataPaymentCode().isPresent())
                    .as("%s is neither in this test's expected mapping nor one of the three known "
                            + "to have no AADE code. Decide its annex 8.12 code or add it to the "
                            + "open list deliberately.", method)
                    .isEqualTo(mapped);
        }
    }

    @Test
    @DisplayName("an unmapped method refuses to substitute a plausible code")
    void anUnmappedMethodRefusesToGuess() {
        // Phase 7's obligation, the same stance as vat_exemption_reason.mydata_code and
        // unit_of_measure.mydata_code: a document transmitted under the wrong payment method is a
        // misdeclared filing that looks successful.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(SettlementMethod.PAYPAL::requireMydataPaymentCode)
                .withMessageContaining("PAYPAL")
                .withMessageContaining("annex 8.12");

        assertThat(SettlementMethod.CASH.requireMydataPaymentCode()).isEqualTo(3);
    }

    @Test
    @DisplayName("adding the code changed nothing about the behaviour already on the enum")
    void theExistingBehaviourIsUntouched() {
        // R1a's boundary, asserted rather than assumed: this step adds an attribute and must not
        // move anything the ledger already depends on.
        assertThat(SettlementMethod.CASH.subjectToCashLimit()).isTrue();
        assertThat(SettlementMethod.BANK_DEPOSIT.settlesImmediately()).isFalse();
        assertThat(SettlementMethod.ON_ACCOUNT.settlementAccount()).isEmpty();
        assertThat(SettlementMethod.CARD_POS.settlesImmediately()).isTrue();
    }
}
