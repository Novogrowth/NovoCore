package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Proof that {@link JsonNumberSweep} actually fails.
 *
 * <p>Same discipline as {@code PropertyTest}, the ArchUnit probes and {@code SchemaConventionsIT}:
 * this sweep now runs over every response every endpoint test produces, so if it were silently
 * incapable of failing it would convert step 14's most load-bearing decision into an unexamined
 * assumption while looking like coverage.
 *
 * <p>Needs no database, so it runs under {@code mvn test} in milliseconds.
 */
class JsonNumberSweepTest {

    private static final String JSON = "application/json";

    @Test
    @DisplayName("money as a quoted string is what the rule asks for, and passes")
    void moneyAsStringsPasses() {
        assertThatCode(() -> JsonNumberSweep.check(HttpMethod.GET, "/api/products/1", JSON, """
                {"id":1,"sku":"TEST-01","sellingPrice":{"amount":"12.50","currency":"EUR"}}"""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a bare decimal is caught, and the message says where and what")
    void aFloatingPointNumberIsCaught() {
        assertThatThrownBy(() -> JsonNumberSweep.check(HttpMethod.GET, "/api/products/1", JSON, """
                {"id":1,"sellingPrice":{"amount":12.50,"currency":"EUR"}}"""))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET /api/products/1")
                .hasMessageContaining("$.sellingPrice.amount")
                .hasMessageContaining("should be a string");
    }

    @Test
    @DisplayName("12.505 — the unit cost behind Q45 — is caught wherever it is nested")
    void theQ45CostIsCaughtInsideAnArray() {
        // Q45's defect was a lot at 12.505, and a JSON number cannot carry it: the nearest double
        // is 12.504999999999999449329379785, so a client that received this has already lost it.
        assertThatThrownBy(() -> JsonNumberSweep.check(HttpMethod.GET, "/api/inventory/lots", JSON,
                """
                {"items":[{"id":7,"unitCost":"9.000000"},{"id":8,"unitCost":12.505}]}"""))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.items[1].unitCost = 12.505");
    }

    @Test
    @DisplayName("integers are left alone — an id is genuinely a number")
    void integersAreNotFlagged() {
        assertThatCode(() -> JsonNumberSweep.check(HttpMethod.GET, "/api/products", JSON, """
                {"items":[{"id":1,"lineNumber":2,"quantity":"3.000000"}],"total":17}"""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1.0 is refused too: a value that can carry a decimal point is a decimal")
    void aWholeFloatingPointNumberIsStillRefused() {
        // The trap this closes: a rate or a quantity that happens to be whole today serialises as
        // 1.0, passes a "does it look like money" eye test, and breaks the day it is 0.1.
        assertThatThrownBy(() -> JsonNumberSweep.check(HttpMethod.GET, "/api/x", JSON, """
                {"ratePercent":24.0}"""))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.ratePercent");
    }

    @Test
    @DisplayName("a non-JSON or empty body is not this sweep's business")
    void nonJsonBodiesAreIgnored() {
        assertThatCode(() -> {
            JsonNumberSweep.check(HttpMethod.GET, "/api/x", "text/html", "<html>12.50</html>");
            JsonNumberSweep.check(HttpMethod.GET, "/api/x", JSON, "");
            JsonNumberSweep.check(HttpMethod.GET, "/api/x", JSON, null);
            JsonNumberSweep.check(HttpMethod.GET, "/api/x", null, "{\"a\":1.5}");
            // Malformed JSON belongs to whatever assertion the caller was making about it; a
            // parse failure here would replace a useful message with a confusing one.
            JsonNumberSweep.check(HttpMethod.GET, "/api/x", JSON, "{not json");
        }).doesNotThrowAnyException();
    }
}
