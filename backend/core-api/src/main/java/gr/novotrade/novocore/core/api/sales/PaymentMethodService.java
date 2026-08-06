package gr.novotrade.novocore.core.api.sales;

import java.util.List;
import java.util.Optional;

/**
 * The business's payment methods — <strong>full CRUD. The owner authors every row.</strong>
 *
 * <h2>⚠️ This interface used to say "there is no create and there never will be"</h2>
 *
 * <p>It shipped in R2b as a seed-only list, one row per {@code SettlementMethod} value, on the
 * reasoning that a new method needs an account key and two behaviour flags no form can supply.
 * <strong>The requirement was wrong, not the implementation.</strong> Payment methods are a business
 * list that <em>references</em> an AADE codification — the same two layers R1a established for
 * document types, one entity over — so the list ships <strong>empty</strong> and the user creates
 * every row.
 *
 * <p>⭐ The old argument survives intact and changed sign: what it named as the reason creation was
 * impossible is now the field list of {@link NewPaymentMethod}.
 *
 * <h2>Editable while unused, frozen once used</h2>
 *
 * <p>⚠️ <strong>Every field</strong> is correctable while no sales invoice names this method, and
 * refused afterwards — the abbreviation and description appear on nothing yet, the article decides
 * what a document declares to AADE, and the account decides where the money was posted. The last of
 * those is why the freeze covers everything rather than a chosen few: changing the account of a method
 * that has already posted would make the ledger disagree with itself.
 *
 * <p>The predicate is {@code PaymentMethodView.inUse}, and ⚠️ a <strong>reversed</strong> invoice
 * counts — it was recorded, it is in the journal, and "recorded" is not "standing".
 */
public interface PaymentMethodService {

    /** ⚠️ Ordered by sort code. */
    List<PaymentMethodView> all();

    /** Active methods only — what a document form offers. */
    List<PaymentMethodView> active();

    Optional<PaymentMethodView> find(long id);

    /** @throws PaymentMethodNotFoundException if the id names nothing */
    PaymentMethodView require(long id);

    /**
     * Creates one.
     *
     * @throws InvalidPaymentMethodException if the abbreviation or sort code is already used, or the
     *     account is not one a payment method may reconcile to
     * @throws PaymentMethodNotFoundException never; the article and account are resolved and their
     *     own not-found exceptions surface instead
     */
    PaymentMethodView create(NewPaymentMethod request);

    /** @throws InvalidPaymentMethodException if blank, already used, or the method is in use */
    PaymentMethodView changeAbbreviation(long id, String abbreviation);

    /** @throws InvalidPaymentMethodException if blank, or the method is in use */
    PaymentMethodView describe(long id, String description);

    /** @throws InvalidPaymentMethodException if the article is inactive, or the method is in use */
    PaymentMethodView changeArticle(long id, long aadePaymentMethodId);

    /**
     * Repoints it at a different account.
     *
     * @throws InvalidPaymentMethodException if the account is not a permitted target, is inactive, or
     *     the method is in use
     */
    PaymentMethodView changeAccount(long id, long accountId);

    /**
     * Reorders it. ⚠️ <strong>Not subject to the in-use freeze</strong> — reordering is normal and a
     * sort code appears on no document. Same exemption as {@code V34}'s four tables.
     *
     * @throws InvalidPaymentMethodException if another method already holds that sort code
     */
    PaymentMethodView changeSortCode(long id, int sortCode);

    /**
     * Takes a method out of use for NEW documents.
     *
     * <p>⚠️ <strong>Setting is refused; holding is not.</strong> Invoices already settled by this
     * method are untouched and stay explicable; what changes is that {@code SalesInvoiceServiceImpl}
     * refuses to record a new one against it.
     */
    void deactivate(long id);

    void reactivate(long id);
}
