package gr.novotrade.novocore.core.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.sales.InvalidPaymentMethodException;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Payment methods — the presentation half of {@link SettlementMethod}.
 *
 * <h2>⚠️ The load-bearing test here is the DRIFT test, and it fails in BOTH directions</h2>
 *
 * <p>A table with one row per enum value is two records of one thing unless something holds them
 * together. One direction alone is not enough:
 *
 * <ul>
 *   <li>only "every row has an enum value" passes while a newly added enum constant has no row —
 *       and {@code require} would then throw for a method the rest of the system happily accepts;
 *   <li>only "every enum value has a row" passes while a stale row survives a constant being
 *       renamed, and the screen offers a method nothing can settle with.
 * </ul>
 *
 * <p>SQL cannot see a Java enum, so {@code V35}'s {@code DO} block can only count. This is the half
 * that compares the <em>names</em>.
 */
class PaymentMethodIT extends AbstractCoreIntegrationTest {

    @Autowired
    private PaymentMethodService paymentMethods;

    @Test
    @DisplayName("⚠️ the table and the enum never drift — asserted in BOTH directions")
    void everyEnumValueHasExactlyOneRowAndEveryRowAnEnumValue() {
        List<PaymentMethodView> rows = paymentMethods.all();

        // → Every enum value has a row. Fails when somebody adds a constant without a migration.
        assertThat(rows).extracting(PaymentMethodView::method)
                .as("a SettlementMethod value with no payment_method row: add one in a migration")
                .containsExactlyInAnyOrder(SettlementMethod.values());

        // ← Every row has an enum value, and there is exactly one. `containsExactlyInAnyOrder`
        //   already rejects duplicates and extras, but the count is stated so a reader does not
        //   have to know that.
        assertThat(rows).hasSize(SettlementMethod.values().length);
        assertThat(rows).extracting(PaymentMethodView::method).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("⚠️ the myDATA code is READ FROM THE ENUM and is not a column")
    void theMydataCodeComesFromTheEnum() {
        // ⚠️ This is the premise correction R2b's brief needed: the codes have been on the enum
        // since it was written, so storing them would create the second record of one thing that
        // the drift test above exists to prevent. Asserting they agree is not enough — there must
        // be nothing to disagree — so this asserts the VIEW carries the ENUM's value, which is only
        // possible because it is resolved rather than stored.
        for (PaymentMethodView row : paymentMethods.all()) {
            assertThat(row.mydataPaymentCodeIfAny())
                    .as("%s's myDATA code must be the enum's", row.method())
                    .isEqualTo(row.method().mydataPaymentCode());
            assertThat(row.settlesImmediately()).isEqualTo(row.method().settlesImmediately());
            assertThat(row.subjectToCashLimit()).isEqualTo(row.method().subjectToCashLimit());
        }

        // The five that are known, and the three that are genuinely open and were NOT invented.
        assertThat(paymentMethods.require(SettlementMethod.CASH).mydataPaymentCode()).isEqualTo(3);
        assertThat(paymentMethods.require(SettlementMethod.BANK_DEPOSIT).mydataPaymentCode())
                .isEqualTo(1);
        assertThat(paymentMethods.require(SettlementMethod.CARD_POS).mydataPaymentCode())
                .isEqualTo(7);
        assertThat(paymentMethods.require(SettlementMethod.ON_ACCOUNT).mydataPaymentCode())
                .isEqualTo(5);
        assertThat(paymentMethods.require(SettlementMethod.SKROUTZ).mydataPaymentCode())
                .isEqualTo(5);

        assertThat(Arrays.asList(SettlementMethod.ACS_COD, SettlementMethod.PAYPAL,
                        SettlementMethod.STRIPE))
                .as("open, and not invented")
                .allSatisfy(method ->
                        assertThat(paymentMethods.require(method).mydataPaymentCodeIfAny())
                                .isEmpty());
    }

    @Test
    @DisplayName("the list is ordered by sort code, not by enum declaration order")
    void orderedBySortCode() {
        // The whole reason the column exists: eight options in declaration order is not a sensible
        // list for somebody choosing while recording a sale.
        assertThat(paymentMethods.all()).extracting(PaymentMethodView::sortCode).isSorted();
    }

    @Test
    @DisplayName("describe, reorder, deactivate and reactivate round-trip")
    void theEditableFieldsRoundTrip() {
        PaymentMethodView paypal = paymentMethods.require(SettlementMethod.PAYPAL);

        assertThat(paymentMethods.describe(SettlementMethod.PAYPAL, "PayPal (test)").description())
                .isEqualTo("PayPal (test)");
        paymentMethods.describe(SettlementMethod.PAYPAL, paypal.description());

        int free = paymentMethods.all().stream()
                .mapToInt(PaymentMethodView::sortCode).max().orElse(0) + 10;
        assertThat(paymentMethods.changeSortCode(SettlementMethod.PAYPAL, free).sortCode())
                .isEqualTo(free);

        // ⚠️ Unique, so the ordering is deterministic.
        int taken = paymentMethods.require(SettlementMethod.CASH).sortCode();
        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> paymentMethods.changeSortCode(SettlementMethod.PAYPAL, taken))
                .withMessageContaining("already used");

        paymentMethods.deactivate(SettlementMethod.PAYPAL);
        assertThat(paymentMethods.require(SettlementMethod.PAYPAL).active()).isFalse();
        assertThat(paymentMethods.active()).extracting(PaymentMethodView::method)
                .doesNotContain(SettlementMethod.PAYPAL);

        paymentMethods.reactivate(SettlementMethod.PAYPAL);
        assertThat(paymentMethods.require(SettlementMethod.PAYPAL).active()).isTrue();
        paymentMethods.changeSortCode(SettlementMethod.PAYPAL, paypal.sortCode());
    }

    @Test
    @DisplayName("a blank description is refused with a reason, not stored")
    void aBlankDescriptionIsRefused() {
        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> paymentMethods.describe(SettlementMethod.STRIPE, "   "))
                .withMessageContaining("must not be blank");
    }
}
