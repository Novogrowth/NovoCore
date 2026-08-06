package gr.novotrade.novocore.core.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodView;
import gr.novotrade.novocore.core.api.sales.InvalidPaymentMethodException;
import gr.novotrade.novocore.core.api.sales.NewPaymentMethod;
import gr.novotrade.novocore.core.api.sales.PaymentMethodNotFoundException;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Payment methods — ⚠️ <strong>a business list the owner authors, not a seed.</strong>
 *
 * <h2>What this class asserted before R4, and why none of it survives</h2>
 *
 * <p>It tested a seed-only table with one row per {@code SettlementMethod} value, and its central
 * assertion was a <strong>bidirectional drift test</strong>: every enum value has exactly one row and
 * every row an enum value. <strong>There is no enum now and no seed</strong>, so that test has no
 * subject — it is not weakened, it is gone with the thing it described.
 *
 * <p>What replaces it is the pair of properties the new model actually has: the list starts
 * <strong>empty</strong>, and every row names both an AADE article and an account.
 */
class PaymentMethodIT extends AbstractCoreIntegrationTest {

    @Autowired
    private PaymentMethodService paymentMethods;

    @Autowired
    private AadePaymentMethodService articles;

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    // -----------------------------------------------------------------------------------------
    // The statutory layer
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("annex 8.12 is seeded with its eight articles, in code order, and 3 is Μετρητά")
    void theCodificationIsSeeded() {
        List<AadePaymentMethodView> all = articles.all();

        assertThat(all).hasSize(8);
        assertThat(all).extracting(AadePaymentMethodView::code)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

        // ⚠️ The one the cash limit derives from. Read from a rasterised page, because
        // paymentMethods-v2.0.1.xsd carries a RANGE and not an enumeration — see V37.
        assertThat(article(3).description()).isEqualTo("Μετρητά");
    }

    @Test
    @DisplayName("⚠️ the codification has no create path — a row here is AADE's, not ours")
    void theCodificationCannotBeAuthored() {
        // StatutoryCodificationRulesTest makes this a build failure; asserting the shape here as
        // well is what stops somebody adding a create and only finding out from an ArchUnit rule
        // whose message they do not recognise.
        assertThat(AadePaymentMethodService.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("create");
    }

    // -----------------------------------------------------------------------------------------
    // The business layer
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ the list starts EMPTY — the eight invented abbreviations are gone")
    void theListStartsEmpty() {
        // ⚠️ This is R4's whole requirement in one line. V35 seeded ΜΕΤΡ, ΚΑΡΤ, ΤΡΑΠ, ΕΠΙΤ, ΑΝΤΙΚ,
        // SKRZ, PPAL and STRP — all INVENTED here rather than chosen by the owner. An empty list
        // removes that rather than making fabrications editable, exactly as R1a's six tables did.
        //
        // It reads what V37 left, before this class or any fixture creates anything.
        assertThat(paymentMethods.all())
                .as("V37 must ship payment_method empty")
                .noneMatch(method -> List.of("ΜΕΤΡ", "ΚΑΡΤ", "ΤΡΑΠ", "ΕΠΙΤ", "ΑΝΤΙΚ", "SKRZ",
                        "PPAL", "STRP").contains(method.abbreviation()));
    }

    @Test
    @DisplayName("a created method carries its article and its account, and both are readable")
    void aCreatedMethodCarriesBoth() {
        AccountView cash = chartOfAccounts.requireAccount(AccountSystemKey.CASH);
        PaymentMethodView created = create("Cash drawer", 3, cash);

        assertThat(created.aadePaymentMethodCode()).isEqualTo(3);
        assertThat(created.aadePaymentMethodDescription()).isEqualTo("Μετρητά");
        assertThat(created.accountId()).isEqualTo(cash.id());
        assertThat(created.accountName()).isEqualTo(cash.name());
        assertThat(created.inUse()).isFalse();
        assertThat(created.active()).isTrue();
    }

    @Test
    @DisplayName("⭐ two methods share AADE code 7 and land in DIFFERENT accounts — the owner's own case")
    void twoMethodsShareAnArticleAndDifferByAccount() {
        // The requirement in one test: the article says what the document declares, the account says
        // where the money went, and the article cannot tell you which.
        PaymentMethodView pos = create("POS terminal 1", 7,
                chartOfAccounts.requireAccount(AccountSystemKey.PARTNER_CLEARING_POS));
        PaymentMethodView paypal = create("POS terminal 2", 7,
                chartOfAccounts.requireAccount(AccountSystemKey.PAYPAL));

        assertThat(pos.aadePaymentMethodCode()).isEqualTo(paypal.aadePaymentMethodCode());
        assertThat(pos.accountId()).isNotEqualTo(paypal.accountId());
    }

    // -----------------------------------------------------------------------------------------
    // The two derived facts
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ settlesImmediately DERIVES from the account's kind, and is not stored")
    void settlingImmediatelyDerivesFromTheAccount() {
        PaymentMethodView cash = create("Cash", 3,
                chartOfAccounts.requireAccount(AccountSystemKey.CASH));
        PaymentMethodView clearing = create("Skroutz", 5,
                chartOfAccounts.requireAccount(AccountSystemKey.PARTNER_CLEARING_SKROUTZ));
        PaymentMethodView onCredit = create("On credit", 5,
                chartOfAccounts.requireAccount(AccountSystemKey.ACCOUNTS_RECEIVABLE));

        assertThat(cash.settlesImmediately()).isTrue();
        assertThat(clearing.settlesImmediately()).isTrue();

        // ⚠️ The case that makes the account mandatory rather than optional: Επί πιστώσει NAMES
        // Accounts receivable instead of carrying a null the posting code reads by convention.
        assertThat(onCredit.settlesImmediately()).isFalse();
        assertThat(onCredit.accountId())
                .isEqualTo(chartOfAccounts.requireAccount(AccountSystemKey.ACCOUNTS_RECEIVABLE).id());
    }

    @Test
    @DisplayName("⚠️ subjectToCashLimit DERIVES from AADE article 3, not from the account")
    void theCashLimitDerivesFromTheArticle() {
        PaymentMethodView cash = create("Cash", 3,
                chartOfAccounts.requireAccount(AccountSystemKey.CASH));
        PaymentMethodView card = create("Card", 7,
                chartOfAccounts.requireAccount(AccountSystemKey.PARTNER_CLEARING_POS));

        assertThat(cash.subjectToCashLimit()).isTrue();
        assertThat(card.subjectToCashLimit()).isFalse();

        // ⚠️ NEGATIVE CONTROL for the derivation itself: a method pointing at the CASH ACCOUNT but
        // declaring a non-cash article is NOT subject to the limit. Without this the test would pass
        // just as happily against an implementation that read the account instead of the article,
        // and the two only differ here.
        PaymentMethodView cashAccountButNotCashArticle = create("Cash-ish", 1,
                chartOfAccounts.requireAccount(AccountSystemKey.CASH));
        assertThat(cashAccountButNotCashArticle.subjectToCashLimit()).isFalse();
    }

    // -----------------------------------------------------------------------------------------
    // Refusals
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("an account that is not a permitted target is refused, with the reason")
    void anImpermissibleAccountIsRefused() {
        // Revenue is not somewhere money lands and is not a receivable, so it is neither branch.
        AccountView sales = chartOfAccounts.requireAccount(AccountSystemKey.SALES_ECOMMERCE);

        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> create("Revenue", 3, sales))
                .withMessageContaining("not one a payment method may reconcile to");
    }

    @Test
    @DisplayName("a duplicate abbreviation and a duplicate sort code are each refused")
    void duplicatesAreRefused() {
        AccountView cash = chartOfAccounts.requireAccount(AccountSystemKey.CASH);
        PaymentMethodView first = create("First", 3, cash);

        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> paymentMethods.create(new NewPaymentMethod(
                        first.abbreviation(), "Another", article(3).id(), cash.id(), nextSortCode())))
                .withMessageContaining("already used");

        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> paymentMethods.create(new NewPaymentMethod(
                        "OTHER" + SEQUENCE.getAndIncrement(), "Another", article(3).id(), cash.id(),
                        first.sortCode())))
                .withMessageContaining("Sort code");
    }

    @Test
    @DisplayName("an id that names nothing is a 404-shaped not-found, not a bad request")
    void anUnknownIdIsNotFound() {
        assertThatExceptionOfType(PaymentMethodNotFoundException.class)
                .isThrownBy(() -> paymentMethods.require(999_999L));
    }

    // -----------------------------------------------------------------------------------------
    // Editing
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("every field round-trips while the method is unused")
    void theEditableFieldsRoundTrip() {
        AccountView cash = chartOfAccounts.requireAccount(AccountSystemKey.CASH);
        PaymentMethodView method = create("Round trip", 3, cash);
        long id = method.id();

        assertThat(paymentMethods.changeAbbreviation(id, "RT" + SEQUENCE.getAndIncrement())
                .abbreviation()).startsWith("RT");
        assertThat(paymentMethods.describe(id, "Corrected").description()).isEqualTo("Corrected");
        assertThat(paymentMethods.changeArticle(id, article(7).id()).aadePaymentMethodCode())
                .isEqualTo(7);

        AccountView pos = chartOfAccounts.requireAccount(AccountSystemKey.PARTNER_CLEARING_POS);
        assertThat(paymentMethods.changeAccount(id, pos.id()).accountId()).isEqualTo(pos.id());

        int free = nextSortCode();
        assertThat(paymentMethods.changeSortCode(id, free).sortCode()).isEqualTo(free);

        paymentMethods.deactivate(id);
        assertThat(paymentMethods.require(id).active()).isFalse();
        assertThat(paymentMethods.active()).extracting(PaymentMethodView::id).doesNotContain(id);

        paymentMethods.reactivate(id);
        assertThat(paymentMethods.require(id).active()).isTrue();
    }

    @Test
    @DisplayName("a blank description and a blank abbreviation are each refused with a reason")
    void blanksAreRefused() {
        PaymentMethodView method = create("Blanks", 3,
                chartOfAccounts.requireAccount(AccountSystemKey.CASH));

        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> paymentMethods.describe(method.id(), "   "))
                .withMessageContaining("must not be blank");
        assertThatExceptionOfType(InvalidPaymentMethodException.class)
                .isThrownBy(() -> paymentMethods.changeAbbreviation(method.id(), "  "))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("the list comes back in sort-code order")
    void theListIsOrdered() {
        create("Order A", 3, chartOfAccounts.requireAccount(AccountSystemKey.CASH));
        create("Order B", 7, chartOfAccounts.requireAccount(AccountSystemKey.PARTNER_CLEARING_POS));

        assertThat(paymentMethods.all()).extracting(PaymentMethodView::sortCode).isSorted();
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private AadePaymentMethodView article(int code) {
        return articles.all().stream()
                .filter(candidate -> candidate.code() == code)
                .findFirst()
                .orElseThrow();
    }

    private PaymentMethodView create(String description, int articleCode, AccountView account) {
        int n = SEQUENCE.getAndIncrement();
        return paymentMethods.create(new NewPaymentMethod(
                "PMIT" + n, description + " " + n, article(articleCode).id(), account.id(),
                nextSortCode()));
    }

    private int nextSortCode() {
        return paymentMethods.all().stream()
                .mapToInt(PaymentMethodView::sortCode).max().orElse(0) + 10;
    }
}
