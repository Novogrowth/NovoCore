package gr.novotrade.novocore.core.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.account.NewAccount;
import gr.novotrade.novocore.core.api.charge.ChargeTypeNotFoundException;
import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeView;
import gr.novotrade.novocore.core.api.charge.InvalidChargeTypeException;
import gr.novotrade.novocore.core.api.charge.NewChargeType;
import gr.novotrade.novocore.core.api.tax.NewVatClass;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Chargeable fee types, and specifically the guard that keeps them on the income side.
 *
 * <p>Nothing consumes charge types yet — Sales Invoice line items are step 9 — so these tests
 * cover the lookup and its validation rather than any invoicing behaviour.
 */
class ChargeTypeIT extends AbstractCoreIntegrationTest {

    @Autowired
    private ChargeTypeService chargeTypes;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    @Test
    @DisplayName("V7 seeds Delivery and COD fee against their own dedicated income accounts (Q27)")
    void seededAgainstDedicatedIncomeAccounts() {
        // The decision this asserts: dedicated accounts rather than Other income. Both halves
        // matter — that the two rows exist, and specifically where they post. Seeding them
        // against the wrong income account would mean migrating posted history later, which is
        // why the rows waited for the decision instead of defaulting.
        assertThat(chargeTypes.active()).extracting(ChargeTypeView::name)
                .contains("Delivery", "COD fee");

        ChargeTypeView delivery = requireChargeType("Delivery");
        ChargeTypeView cod = requireChargeType("COD fee");

        assertThat(delivery.incomeAccountId()).isEqualTo(accountNamed("Delivery income").id());
        assertThat(cod.incomeAccountId()).isEqualTo(accountNamed("COD fee income").id());
        assertThat(delivery.incomeAccountId())
                .as("not the residual bucket, and not each other's account")
                .isNotEqualTo(otherIncome().id())
                .isNotEqualTo(cod.incomeAccountId());

        // Both default to the standard rate. Note the known limitation recorded in V7: a delivery
        // charge ancillary to a supply legally follows the rate of the goods it delivers, and
        // nothing derives that automatically — the per-line override is the only route to it.
        long standardRate = vatClasses.requireByCode("1410").id();
        assertThat(delivery.defaultVatClassId()).isEqualTo(standardRate);
        assertThat(cod.defaultVatClassId()).isEqualTo(standardRate);
    }

    @Test
    @DisplayName("a charge type round-trips against a real income account and VAT class")
    void createAgainstOtherIncome() {
        AccountView otherIncome = otherIncome();
        VatClassView standardRate = vatClasses.requireByCode("1410");

        ChargeTypeView created = chargeTypes.create(
                new NewChargeType("Test COD fee", standardRate.id(), otherIncome.id()));

        assertThat(created.name()).isEqualTo("Test COD fee");
        assertThat(created.defaultVatClassId()).isEqualTo(standardRate.id());
        assertThat(created.incomeAccountId()).isEqualTo(otherIncome.id());
        assertThat(created.active()).isTrue();

        assertThat(chargeTypes.findByName("test cod fee"))
                .as("lookup by name is case-insensitive")
                .isPresent();
        assertThat(chargeTypes.active()).extracting(ChargeTypeView::name)
                .contains("Test COD fee");
    }

    // ---------------------------------------------------------------------------------------
    // The income-side guard
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("an expense account is refused — a fee is revenue, not a cost offset")
    void expenseAccountIsRefused() {
        // The mistake this prevents is specific and plausible: wiring a delivery fee to
        // Transportation costs to "net it off". That understates revenue and cost together and
        // leaves a gross margin that looks entirely reasonable while being wrong.
        AccountView transportationCosts = accountNamed("Transportation costs");
        assertThat(transportationCosts.type()).isEqualTo(AccountType.EXPENSE);

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test delivery netted off",
                        vatClasses.requireByCode("1410").id(),
                        transportationCosts.id())))
                .withMessageContaining("must post to an INCOME account")
                .withMessageContaining("not a reduction of an expense");
    }

    @Test
    @DisplayName("a contra-income account is refused — that side is for sales returns")
    void contraIncomeAccountIsRefused() {
        AccountView salesReturns = accountNamed("Sales returns — eCommerce");
        assertThat(salesReturns.type()).isEqualTo(AccountType.CONTRA_INCOME);

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test fee against returns",
                        vatClasses.requireByCode("1410").id(),
                        salesReturns.id())))
                .withMessageContaining("must post to an INCOME account");
    }

    @Test
    @DisplayName("an asset account is refused too")
    void assetAccountIsRefused() {
        AccountView cash = accountNamed("Cash");

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test fee against cash",
                        vatClasses.requireByCode("1410").id(),
                        cash.id())))
                .withMessageContaining("must post to an INCOME account");
    }

    @Test
    @DisplayName("an inactive income account is refused")
    void inactiveAccountIsRefused() {
        // In a group of its own, not in the seeded Income group. These tests share one database
        // and are not transactional, so an extra account inside a seeded group would break
        // ChartOfAccountsIT's per-group counts depending on which class happened to run first.
        AccountView temporary = chartOfAccounts.createAccount(NewAccount.standard(
                "Test income to deactivate",
                AccountType.INCOME,
                chartOfAccounts.createGroup("Test — charge type income").id()));
        chartOfAccounts.deactivate(temporary.id());

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test fee against inactive account",
                        vatClasses.requireByCode("1410").id(),
                        temporary.id())))
                .withMessageContaining("inactive");
    }

    @Test
    @DisplayName("an unknown account or VAT class is refused by name, not by integrity violation")
    void unknownReferencesAreRefused() {
        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test unknown account",
                        vatClasses.requireByCode("1410").id(),
                        999_999L)))
                .withMessageContaining("No account with id 999999");

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test unknown vat class", 999_999L, otherIncome().id())))
                .withMessageContaining("No VAT class with id 999999");
    }

    @Test
    @DisplayName("an inactive VAT class cannot be a default")
    void inactiveVatClassIsRefused() {
        VatClassView temporary = vatClasses.create(new NewVatClass(
                "TEST-CHARGE-INACTIVE", "Inactive rate (test)", new BigDecimal("12")));
        vatClasses.deactivate(temporary.id());

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "Test fee inactive rate", temporary.id(), otherIncome().id())))
                .withMessageContaining("inactive");
    }

    // ---------------------------------------------------------------------------------------
    // Ordinary lookup behaviour
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a duplicate name is refused")
    void duplicateNameIsRefused() {
        chargeTypes.create(new NewChargeType(
                "Test duplicate fee", vatClasses.requireByCode("1410").id(), otherIncome().id()));

        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.create(new NewChargeType(
                        "test duplicate fee",
                        vatClasses.requireByCode("1410").id(),
                        otherIncome().id())))
                .withMessageContaining("already exists");
    }

    @Test
    @DisplayName("the VAT class and income account can be changed, with the same guards")
    void changeReferences() {
        ChargeTypeView chargeType = chargeTypes.create(new NewChargeType(
                "Test changeable fee",
                vatClasses.requireByCode("1410").id(),
                otherIncome().id()));

        VatClassView reducedRate = vatClasses.requireByCode("1170");
        assertThat(chargeTypes.changeDefaultVatClass(chargeType.id(), reducedRate.id())
                .defaultVatClassId()).isEqualTo(reducedRate.id());

        AccountView services = accountNamed("Services");
        assertThat(chargeTypes.changeIncomeAccount(chargeType.id(), services.id())
                .incomeAccountId()).isEqualTo(services.id());

        // The guard applies on change, not only on create.
        assertThatExceptionOfType(InvalidChargeTypeException.class)
                .isThrownBy(() -> chargeTypes.changeIncomeAccount(
                        chargeType.id(), accountNamed("Rent").id()))
                .withMessageContaining("must post to an INCOME account");
    }

    @Test
    @DisplayName("a charge type can be renamed, deactivated and reactivated")
    void renameAndDeactivate() {
        ChargeTypeView chargeType = chargeTypes.create(new NewChargeType(
                "Test fee before rename",
                vatClasses.requireByCode("1410").id(),
                otherIncome().id()));

        assertThat(chargeTypes.rename(chargeType.id(), "Test fee after rename").name())
                .isEqualTo("Test fee after rename");

        chargeTypes.deactivate(chargeType.id());
        assertThat(chargeTypes.require(chargeType.id()).active()).isFalse();
        assertThat(chargeTypes.active()).extracting(ChargeTypeView::id)
                .doesNotContain(chargeType.id());

        chargeTypes.reactivate(chargeType.id());
        assertThat(chargeTypes.require(chargeType.id()).active()).isTrue();
    }

    @Test
    @DisplayName("a missing charge type names the id it wanted")
    void missingChargeType() {
        assertThatExceptionOfType(ChargeTypeNotFoundException.class)
                .isThrownBy(() -> chargeTypes.require(999_999L))
                .withMessageContaining("999999");

        assertThat(chargeTypes.find(999_999L)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private ChargeTypeView requireChargeType(String name) {
        return chargeTypes.findByName(name).orElseThrow(() -> new AssertionError(
                "Charge type '" + name + "' is missing; V7 should have seeded it."));
    }

    private AccountView otherIncome() {
        return accountNamed("Other income");
    }

    private AccountView accountNamed(String name) {
        return chartOfAccounts.allAccounts().stream()
                .filter(account -> account.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Seeded account '" + name + "' is missing; V4 should have created it. "
                                + "Rounding account for reference: "
                                + chartOfAccounts.requireAccount(
                                        AccountSystemKey.ROUNDING_DIFFERENCES).name()));
    }
}
