package gr.novotrade.novocore.core.web.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * How money crosses the wire, held to the one figure that proves why it matters.
 *
 * <p><strong>12.505 is not an arbitrary example.</strong> It is the unit cost that produced Q45 — a
 * 22-unit lot at 12.505 left −0.11 EUR permanently stranded in the Inventory control account,
 * because two roundings of the same quantity disagreed. ADR 0015 fixed the arithmetic. This test
 * exists so the REST layer cannot reintroduce the same class of defect one layer further out, by
 * handing that number to a parser that cannot hold it.
 */
class NovoCoreJsonModuleTest {

    private final JsonMapper json = JsonMapper.builder()
            .addModule(new NovoCoreJsonModule())
            .build();

    @Nested
    @DisplayName("12.505 — the figure behind Q45")
    class TheFigureBehindQ45 {

        @Test
        @DisplayName("a double cannot hold it, which is the whole reason for the string format")
        void aDoubleCannotHoldIt() {
            // Not a claim about Jackson — a claim about IEEE-754, and the reason a JSON number
            // literal is unsafe for an amount no matter which library reads it. A JavaScript client
            // has no other numeric type to parse into.
            BigDecimal exact = new BigDecimal("12.505");
            BigDecimal viaDouble = new BigDecimal(12.505d);

            assertThat(viaDouble).isNotEqualTo(exact);
            // The nearest double to 12.505 is slightly *above* it, and carries 45 decimal places
            // of noise. Rounded to a cent that is 12.51 rather than 12.50 under HALF_UP — a
            // one-cent difference arrived at by a value nobody entered.
            assertThat(viaDouble.toPlainString())
                    .isEqualTo("12.5050000000000007815970093361102044582366943359375");
        }

        @Test
        @DisplayName("it survives a round trip as a UnitCost, exactly")
        void itRoundTripsExactly() {
            UnitCost cost = UnitCost.ofEur("12.505");

            String written = json.writeValueAsString(cost);
            UnitCost read = json.readValue(written, UnitCost.class);

            assertThat(written).isEqualTo("{\"amount\":\"12.505000\",\"currency\":\"EUR\"}");
            assertThat(read).isEqualTo(cost);
            assertThat(read.value()).isEqualByComparingTo(exactly("12.505"));
        }

        @Test
        @DisplayName("sent as a JSON number it is refused, not quietly accepted")
        void sentAsANumberItIsRefused() {
            // The bytes on the wire may still read 12.505, but the client's own variable did not —
            // it held a double, and the next value it computes will be wrong. Accepting this would
            // make the backend the last place the mistake could be caught and the one place it was
            // not.
            assertThatExceptionOfType(DatabindException.class)
                    .isThrownBy(() -> json.readValue(
                            "{\"amount\": 12.505, \"currency\": \"EUR\"}", UnitCost.class))
                    .withMessageContaining("must be a JSON string");
        }

        @Test
        @DisplayName("as a Money it is refused for a different reason: three decimals is not a cent")
        void asMoneyItIsRefusedForBeingTooPrecise() {
            // Money normalises to two decimals and rejects anything more precise rather than
            // rounding it. That behaviour should apply to a request body exactly as it does inside
            // the core: a caller sending 12.505 as a price has made a mistake worth naming.
            assertThatExceptionOfType(Exception.class)
                    .isThrownBy(() -> json.readValue(
                            "{\"amount\": \"12.505\", \"currency\": \"EUR\"}", Money.class));
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("an amount is a quoted string, never a JSON number")
        void amountsAreStrings() {
            String written = json.writeValueAsString(Money.ofEur("12.50"));

            assertThat(written).isEqualTo("{\"amount\":\"12.50\",\"currency\":\"EUR\"}");
            // The assertion that actually matters: the digits are inside quotes.
            assertThat(written).doesNotContain(":12.50");
        }

        @Test
        @DisplayName("the currency travels with the amount and is never assumed")
        void currencyTravelsWithTheAmount() {
            // ADR 0005 models currency from day one although behaviour is EUR-only. Flattening this
            // to a bare amount would make the boundary the single place that quietly assumes EUR.
            assertThat(json.writeValueAsString(Money.ofEur("1.00"))).contains("\"currency\":\"EUR\"");
        }

        @Test
        @DisplayName("a quantity is a bare string carrying its six decimals")
        void quantitiesAreStrings() {
            assertThat(json.writeValueAsString(Quantity.of(3L))).isEqualTo("\"3.000000\"");
        }

        @Test
        @DisplayName("trailing scale is preserved, so 12.50 does not come back as 12.5")
        void scaleIsPreserved() {
            // A reader displaying the string directly must not have to re-pad it, and a checksum or
            // a comparison over the raw payload must be stable.
            assertThat(json.writeValueAsString(Money.ofEur("12.5"))).contains("\"12.50\"");
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("a bare number instead of the object is refused, naming the reason")
        void aBareNumberIsRefused() {
            assertThatExceptionOfType(DatabindException.class)
                    .isThrownBy(() -> json.readValue("12.50", Money.class))
                    .withMessageContaining("expected an object");
        }

        @Test
        @DisplayName("a missing currency is refused rather than defaulted to EUR")
        void aMissingCurrencyIsRefused() {
            assertThatExceptionOfType(DatabindException.class)
                    .isThrownBy(() -> json.readValue("{\"amount\": \"1.00\"}", Money.class))
                    .withMessageContaining("It is not defaulted");
        }

        @Test
        @DisplayName("an unknown currency code is refused")
        void anUnknownCurrencyIsRefused() {
            assertThatExceptionOfType(DatabindException.class)
                    .isThrownBy(() -> json.readValue(
                            "{\"amount\": \"1.00\", \"currency\": \"XYZ\"}", Money.class))
                    .withMessageContaining("not a known ISO 4217 currency code");
        }

        @Test
        @DisplayName("a non-numeric amount is refused")
        void aNonNumericAmountIsRefused() {
            assertThatExceptionOfType(DatabindException.class)
                    .isThrownBy(() -> json.readValue(
                            "{\"amount\": \"twelve\", \"currency\": \"EUR\"}", Money.class))
                    .withMessageContaining("not a valid decimal number");
        }

        @Test
        @DisplayName("a quantity sent as a JSON number is refused")
        void aQuantityAsANumberIsRefused() {
            assertThatExceptionOfType(DatabindException.class)
                    .isThrownBy(() -> json.readValue("3.5", Quantity.class))
                    .withMessageContaining("must be a JSON string");
        }

        @Test
        @DisplayName("a quantity round-trips at six decimals")
        void quantityRoundTrips() {
            Quantity quantity = Quantity.of(new BigDecimal("2.500000"));

            assertThat(json.readValue(json.writeValueAsString(quantity), Quantity.class))
                    .isEqualTo(quantity);
        }

        @Test
        @DisplayName("surrounding whitespace in a value is tolerated")
        void whitespaceIsTolerated() {
            assertThat(json.readValue(
                    "{\"amount\": \" 1.00 \", \"currency\": \" EUR \"}", Money.class))
                    .isEqualTo(Money.ofEur("1.00"));
        }
    }

    private static BigDecimal exactly(String value) {
        return new BigDecimal(value);
    }
}
