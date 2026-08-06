package gr.novotrade.novocore.core.testsupport;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodView;
import gr.novotrade.novocore.core.api.sales.NewPaymentMethod;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Payment methods for tests, created on demand — because <strong>R4 ships the table EMPTY.</strong>
 *
 * <p>Every sales-invoice test used to name a {@code SettlementMethod} enum constant, which always
 * existed. There is no enum and no seed now, so a test that records a sale must first author the
 * method it settles with, exactly as the owner will.
 *
 * <h2>⚠️ The article mapping here is a TEST convention, not a statutory decision</h2>
 *
 * <p>Four of these are unambiguous in annex 8.12 and are used as such: <strong>3</strong> Μετρητά,
 * <strong>7</strong> POS / e-POS, <strong>5</strong> Επί Πιστώσει, <strong>1</strong> Επαγ.
 * Λογαριασμός Πληρωμών Ημεδαπής.
 *
 * <p>⚠️ <strong>{@link #partnerClearing} deliberately makes the caller name the article</strong>,
 * because the real question — which article a courier cash-on-delivery, PayPal or Stripe method
 * declares — is <strong>HELD with the owner and his accountant</strong> and must not be settled by a
 * test fixture picking something plausible. A test needs <em>an</em> article; production needs
 * <em>the right one</em>, and those are different requirements.
 *
 * <p>⚠️ <strong>{@code cash()} is the one whose article carries behaviour</strong>: article 3 is what
 * {@code PaymentMethodServiceImpl.CASH_ARTICLE_CODE} derives the cash limit from, so a test about
 * that limit must use this method and not merely a method pointing at the cash account.
 */
@Component
public class PaymentMethodFixture {

    /** Annex 8.12, read from a rasterised page. See {@code V37}. */
    public static final int ARTICLE_DOMESTIC_ACCOUNT = 1;
    public static final int ARTICLE_CASH = 3;
    public static final int ARTICLE_ON_CREDIT = 5;
    public static final int ARTICLE_POS = 7;

    private final PaymentMethodService paymentMethods;
    private final AadePaymentMethodService articles;
    private final ChartOfAccountsService chartOfAccounts;
    private final AtomicInteger sequence = new AtomicInteger(1);

    PaymentMethodFixture(PaymentMethodService paymentMethods, AadePaymentMethodService articles,
            ChartOfAccountsService chartOfAccounts) {
        this.paymentMethods = paymentMethods;
        this.articles = articles;
        this.chartOfAccounts = chartOfAccounts;
    }

    /** Settles into the cash box, and is the only fixture subject to the cash limit. */
    public long cash() {
        return create("CASH", ARTICLE_CASH, AccountSystemKey.CASH);
    }

    /** A card at the terminal — held in POS partner clearing until the acquirer remits. */
    public long cardPos() {
        return create("CARD", ARTICLE_POS, AccountSystemKey.PARTNER_CLEARING_POS);
    }

    /**
     * Credit terms.
     *
     * <p>⚠️ It names <strong>Accounts receivable explicitly</strong>, which is R4's point: the
     * invoice stays an open item because of the account it names, not because of a null.
     */
    public long onAccount() {
        return create("CRED", ARTICLE_ON_CREDIT, AccountSystemKey.ACCOUNTS_RECEIVABLE);
    }

    /**
     * A promised bank transfer. ⚠️ Also Accounts receivable — brief §6's decision that a customer
     * saying they have paid is not the money arriving.
     */
    public long bankDeposit() {
        return create("BANK", ARTICLE_DOMESTIC_ACCOUNT, AccountSystemKey.ACCOUNTS_RECEIVABLE);
    }

    /**
     * A partner-clearing method. ⚠️ <strong>The caller names the article on purpose</strong> — see
     * the class javadoc; the production mapping for these is an open question.
     */
    public long partnerClearing(String abbreviationPrefix, int articleCode, AccountSystemKey account) {
        return create(abbreviationPrefix, articleCode, account);
    }

    private int nextSortCode() {
        return paymentMethods.all().stream()
                .mapToInt(PaymentMethodView::sortCode).max().orElse(0) + 10;
    }

    private long create(String abbreviationPrefix, int articleCode, AccountSystemKey account) {
        AadePaymentMethodView article = articles.all().stream()
                .filter(candidate -> candidate.code() == articleCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Annex 8.12 has no article " + articleCode + ". V37 seeds codes 1 to 8."));

        int n = sequence.getAndIncrement();
        PaymentMethodView created = paymentMethods.create(new NewPaymentMethod(
                abbreviationPrefix + n,
                abbreviationPrefix + " " + n + " (test)",
                article.id(),
                chartOfAccounts.requireAccount(account).id(),
                // ⚠️ max + 10, NOT a counter. Test classes share one database, so two fixtures
                // each counting from their own base collide — which is exactly what happened on
                // the first full-suite run: four SalesInvoiceIT tests failed on a sort code
                // PaymentMethodIT had already taken. Ordering is not what these tests are about;
                // uniqueness is, because the column is unique.
                nextSortCode()));
        return created.id();
    }
}
