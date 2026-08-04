package gr.novotrade.novocore.core.api.sales;

import java.util.List;
import java.util.Optional;

/**
 * The business's payment methods — ⚠️ <strong>read, describe, reorder, deactivate, reactivate.</strong>
 *
 * <h2>⚠️ There is no create and no delete, and there never will be</h2>
 *
 * <p>One row per {@link SettlementMethod} value, seeded by Flyway. Adding a payment method is a code
 * change whatever else happens — a new value needs an {@code AccountSystemKey}, a
 * {@code settlesImmediately} and a {@code subjectToCashLimit}, and no form can supply those. This is
 * the same shape as {@code AadeInvoiceTypeService}: the rows are authored elsewhere and the screen
 * says so rather than merely omitting a button.
 *
 * <p>📌 Worth knowing, because the owner's own list includes them: <strong>"Cheque" and "Foreign bank
 * account" are in Prosvasis Go and are NOT values of this enum.</strong> Adding either means choosing
 * an account key and two behaviour flags — the no-create argument stated concretely rather than in
 * the abstract.
 */
public interface PaymentMethodService {

    /** ⚠️ Ordered by sort code, not by enum declaration order. */
    List<PaymentMethodView> all();

    /** What a document form offers. */
    List<PaymentMethodView> active();

    Optional<PaymentMethodView> find(SettlementMethod method);

    /** @throws PaymentMethodNotFoundException if the enum value has no row — a drift bug */
    PaymentMethodView require(SettlementMethod method);

    /** @throws InvalidPaymentMethodException if the description is blank */
    PaymentMethodView describe(SettlementMethod method, String description);

    /**
     * Reorders it. ⚠️ Freely editable — a sort code appears on no document.
     *
     * @throws InvalidPaymentMethodException if another method already holds that sort code
     */
    PaymentMethodView changeSortCode(SettlementMethod method, int sortCode);

    /**
     * Takes a method out of use for NEW documents.
     *
     * <p>⚠️ <strong>Setting is refused; holding is not.</strong> Documents already settled by this
     * method are untouched and stay explicable — the same distinction R2b draws for a series'
     * document type. What changes is that {@code SalesInvoiceServiceImpl} refuses to record a new
     * invoice settled by it.
     */
    void deactivate(SettlementMethod method);

    void reactivate(SettlementMethod method);
}
