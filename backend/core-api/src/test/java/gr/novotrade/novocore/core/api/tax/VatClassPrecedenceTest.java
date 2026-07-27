package gr.novotrade.novocore.core.api.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The stated precedence rule: invoice line beats customer, customer beats product.
 *
 * <p>Exhaustive over all eight combinations of the three levels being present or absent. That is
 * deliberate rather than thorough-for-its-own-sake: the rule is three lines of null checks whose
 * ordering carries the entire meaning, so the only useful test is one that would catch the
 * ordering being changed.
 */
class VatClassPrecedenceTest {

    private static final long LINE = 11L;
    private static final long CUSTOMER = 22L;
    private static final long PRODUCT = 33L;

    @Nested
    @DisplayName("precedence order")
    class Precedence {

        @Test
        @DisplayName("the invoice line wins over both the customer and the product")
        void lineBeatsEverything() {
            VatClassResolution resolved =
                    VatClassPrecedence.resolve(LINE, CUSTOMER, PRODUCT);

            assertThat(resolved.vatClassId()).isEqualTo(LINE);
            assertThat(resolved.source()).isEqualTo(VatClassSource.INVOICE_LINE);
            assertThat(resolved.isOverride()).isTrue();
        }

        @Test
        @DisplayName("the customer wins over the product when the line says nothing")
        void customerBeatsProduct() {
            VatClassResolution resolved =
                    VatClassPrecedence.resolve(null, CUSTOMER, PRODUCT);

            assertThat(resolved.vatClassId()).isEqualTo(CUSTOMER);
            assertThat(resolved.source()).isEqualTo(VatClassSource.CUSTOMER);
            assertThat(resolved.isOverride()).isTrue();
        }

        @Test
        @DisplayName("the product's default applies when nothing overrides it")
        void productIsTheBaseCase() {
            VatClassResolution resolved =
                    VatClassPrecedence.resolve(null, null, PRODUCT);

            assertThat(resolved.vatClassId()).isEqualTo(PRODUCT);
            assertThat(resolved.source()).isEqualTo(VatClassSource.PRODUCT);
            assertThat(resolved.isOverride())
                    .as("the product's own default is not an override")
                    .isFalse();
        }

        @Test
        @DisplayName("the line wins even when it is the only level set")
        void lineAloneWins() {
            assertThat(VatClassPrecedence.resolve(LINE, null, null).source())
                    .isEqualTo(VatClassSource.INVOICE_LINE);
            assertThat(VatClassPrecedence.resolve(LINE, CUSTOMER, null).source())
                    .isEqualTo(VatClassSource.INVOICE_LINE);
            assertThat(VatClassPrecedence.resolve(LINE, null, PRODUCT).source())
                    .isEqualTo(VatClassSource.INVOICE_LINE);
        }

        @Test
        @DisplayName("the customer wins when it is the only level set")
        void customerAloneWins() {
            assertThat(VatClassPrecedence.resolve(null, CUSTOMER, null).source())
                    .isEqualTo(VatClassSource.CUSTOMER);
        }

        @Test
        @DisplayName("the enum declares the levels in precedence order, highest first")
        void enumOrderMatchesPrecedence() {
            // Guards against the enum being reordered and the javadoc quietly becoming a lie.
            assertThat(VatClassSource.values()).containsExactly(
                    VatClassSource.INVOICE_LINE,
                    VatClassSource.CUSTOMER,
                    VatClassSource.PRODUCT);
        }
    }

    @Nested
    @DisplayName("no fallback rate")
    class NoFallback {

        @Test
        @DisplayName("resolve throws rather than assuming a standard rate")
        void resolveThrowsWhenNothingIsSet() {
            // The single most important assertion here. A fallback to 24% would produce a
            // plausible invoice at a rate nobody chose, and an undercharge is not recoverable
            // from the customer once the invoice has been issued.
            assertThatExceptionOfType(VatClassNotDeterminableException.class)
                    .isThrownBy(() -> VatClassPrecedence.resolve(null, null, null))
                    .withMessageContaining("does not fall back to a standard rate");
        }

        @Test
        @DisplayName("find returns empty for callers that can show the line as needing attention")
        void findIsEmptyWhenNothingIsSet() {
            assertThat(VatClassPrecedence.find(null, null, null)).isEmpty();
        }

        @Test
        @DisplayName("find agrees with resolve whenever a class is determinable")
        void findAgreesWithResolve() {
            Long[] levels = {null, 1L};
            for (Long line : levels) {
                for (Long customer : levels) {
                    for (Long product : levels) {
                        var found = VatClassPrecedence.find(line, customer, product);
                        if (found.isEmpty()) {
                            assertThatExceptionOfType(VatClassNotDeterminableException.class)
                                    .isThrownBy(() ->
                                            VatClassPrecedence.resolve(line, customer, product));
                        } else {
                            assertThat(VatClassPrecedence.resolve(line, customer, product))
                                    .isEqualTo(found.orElseThrow());
                        }
                    }
                }
            }
        }
    }
}
