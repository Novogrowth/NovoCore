package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;

/**
 * Authors a payment method over HTTP, for the contract tests that need one.
 *
 * <h2>⚠️ Why every one of them suddenly needs this</h2>
 *
 * <p>Before R4 a sales invoice named a {@code SettlementMethod} enum constant, which always existed.
 * R4 makes it a foreign key and <strong>ships {@code payment_method} EMPTY</strong>, so a test that
 * records an invoice must first author the method it settles with — exactly as the owner will.
 *
 * <p>⚠️ <strong>Article 5 (Επί Πιστώσει) against Accounts receivable</strong>, which is the case R4's
 * reversal turns on: the invoice stays an open item because of the account the method NAMES, not
 * because of a null the posting code interprets. Tests that need a born-settled invoice should author
 * their own against a bank, cash or partner-clearing account rather than widening this one.
 */
final class PaymentMethods {

    private PaymentMethods() {
    }

    /** An on-account method, created fresh. The abbreviation and sort code are unique per call. */
    static long onAccount(ApiClient.Session owner, String discriminator) {
        long article = articleWithCode(owner, 5);
        long receivable = accountWithSystemKey(owner, "ACCOUNTS_RECEIVABLE");

        // ⚠️ Derived from the discriminator rather than a counter. Contract tests share a database
        // and abbreviation and sort code are both UNIQUE — two independent counters starting from
        // their own base is exactly the collision the core fixture already hit once.
        int unique = Math.abs(discriminator.hashCode() % 100_000) + 200_000;
        return Json.createdId(owner.post("/api/payment-methods", """
                {"abbreviation":"PM-%d","description":"TEST on account %s",
                 "aadePaymentMethodId":%d,"accountId":%d,"sortCode":%d}
                """.formatted(unique, discriminator, article, receivable, unique)),
                "the on-account payment method");
    }

    static long articleWithCode(ApiClient.Session owner, int code) {
        for (JsonNode article : Json.items(
                owner.get("/api/aade-payment-methods"), "the AADE payment-method articles")) {
            if (article.get("code").asInt() == code) {
                return article.get("id").asLong();
            }
        }
        throw new AssertionError("Annex 8.12 has no article " + code + ". V37 seeds codes 1 to 8.");
    }

    static long accountWithSystemKey(ApiClient.Session owner, String systemKey) {
        for (JsonNode group : Json.items(owner.get("/api/chart-of-accounts"), "the chart")) {
            for (JsonNode account : group.get("accounts")) {
                if (systemKey.equals(Json.text(account, "systemKey"))) {
                    return account.get("id").asLong();
                }
            }
        }
        throw new AssertionError("No account carries the system key " + systemKey + ".");
    }

    /** Asserts the codification arrived, so a seeding failure is not mistaken for a contract one. */
    static void assertTheCodificationIsSeeded(ApiClient.Session owner) {
        assertThat(Json.items(owner.get("/api/aade-payment-methods"), "annex 8.12")).hasSize(8);
    }
}
