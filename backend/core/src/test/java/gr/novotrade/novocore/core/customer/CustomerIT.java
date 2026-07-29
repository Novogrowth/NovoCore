package gr.novotrade.novocore.core.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.shared.Rate;
import gr.novotrade.novocore.core.api.customer.CustomerNotFoundException;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerSystemKey;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.InvalidCustomerException;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.tax.NewVatClass;
import gr.novotrade.novocore.core.api.tax.VatClassPrecedence;
import gr.novotrade.novocore.core.api.tax.VatClassResolution;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Customers, against a real PostgreSQL. Fixtures are prefixed {@code CustIT} because these tests
 * share one non-transactional database with every other {@code *IT}.
 */
class CustomerIT extends AbstractCoreIntegrationTest {

    @Autowired
    private CustomerService customers;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private VatExemptionReasonService exemptionReasons;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a customer round-trips with a single email and a single phone (Q8)")
    void createAndRead() {
        // Q8 answered: one each, not a multi-value structure. A one-to-many table would have to be
        // joined, rendered and de-duplicated everywhere for a case that has not arisen.
        CustomerView created = customers.create(NewCustomer.retail(
                "CustIT — Μαρία Παπαδοπούλου", "maria@custit.example", "+30 6900000001"));

        assertThat(created.emailIfAny()).contains("maria@custit.example");
        assertThat(created.phoneIfAny()).contains("+30 6900000001");
        assertThat(created.vatStatus()).isEqualTo(VatStatus.DOMESTIC);
        assertThat(created.vatNumberIfAny())
                .as("a retail customer has no ΑΦΜ")
                .isEmpty();

        // Greek survives the round trip.
        assertThat(customers.require(created.id()).name())
                .isEqualTo("CustIT — Μαρία Παπαδοπούλου");
    }

    @Test
    @DisplayName("no external system reference id exists anywhere on the table (rule 2)")
    void noExternalSystemIds() {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'customer'
                """, String.class))
                .noneSatisfy(column -> assertThat(column.toLowerCase())
                        .containsAnyOf("go_", "woo", "external", "skroutz"));
    }

    @Test
    @DisplayName("two customers may share a name; they may not share a VAT number")
    void namesCollideButVatNumbersDoNot() {
        // Two unrelated retail customers genuinely can share a name, and refusing the second would
        // push whoever is serving them into inventing a suffix. The VAT number is the identifier.
        customers.create(NewCustomer.retail("CustIT — Γιώργος Παπαδόπουλος", null, null));
        CustomerView namesake =
                customers.create(NewCustomer.retail("CustIT — Γιώργος Παπαδόπουλος", null, null));

        assertThat(namesake.id()).isPositive();

        customers.create(NewCustomer.domestic("CustIT — Business one", "EL088888001"));
        assertThatExceptionOfType(InvalidCustomerException.class)
                .isThrownBy(() -> customers.create(
                        NewCustomer.domestic("CustIT — Business two", "EL088888001")))
                .withMessageContaining("authoritative identifier");
    }

    // ---------------------------------------------------------------------------------------
    // Q9 — the VAT status classification
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("an intra-EU B2B customer must have a VAT number")
    void intraEuNeedsVatNumber() {
        // Definitional rather than policy: with no counterparty VAT number the supply is a
        // distance sale to a consumer, not a reverse-charged B2B supply, and treating one as the
        // other is a real VAT error rather than a data-quality nit.
        assertThatExceptionOfType(InvalidCustomerException.class)
                .isThrownBy(() -> customers.create(new NewCustomer(
                        "CustIT — German café", null, null, null,
                        VatStatus.INTRA_EU_B2B, null, null)))
                .withMessageContaining("distance sale to a consumer");

        CustomerView withNumber = customers.create(new NewCustomer(
                "CustIT — German café GmbH", null, null, "DE088888002",
                VatStatus.INTRA_EU_B2B, null, null));
        assertThat(withNumber.vatStatus()).isEqualTo(VatStatus.INTRA_EU_B2B);
    }

    @Test
    @DisplayName("an exempt customer names the article, using a real seeded AADE reason")
    void exemptNeedsReason() {
        assertThatExceptionOfType(InvalidCustomerException.class)
                .isThrownBy(() -> customers.create(new NewCustomer(
                        "CustIT — Exempt no reason", null, null, null,
                        VatStatus.EXEMPT, null, null)))
                .withMessageContaining("exemption reason");

        // Code 6 is "Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ" in the seeded list.
        long reasonId = exemptionReasons.requireByCode(6).id();
        CustomerView exempt = customers.create(new NewCustomer(
                "CustIT — Exempt body", null, null, null, VatStatus.EXEMPT, null, reasonId));

        assertThat(exempt.vatExemptionReason()).contains(reasonId);
        assertThat(exemptionReasons.require(reasonId).description())
                .isEqualTo("Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ");
    }

    @Test
    @DisplayName("all five statuses are storable, including non-EU export as its own case")
    void everyStatusIsStorable() {
        // NON_EU_EXPORT is deliberately not folded into OTHER: an export and an intra-EU supply are
        // both VAT-free under different articles and are reported differently.
        assertThat(customers.create(new NewCustomer(
                "CustIT — US buyer", null, null, null, VatStatus.NON_EU_EXPORT, null, null))
                .vatStatus()).isEqualTo(VatStatus.NON_EU_EXPORT);

        // OTHER exists so an unusual party can be recorded truthfully, but nothing defaults to it.
        assertThat(customers.create(new NewCustomer(
                "CustIT — Unusual case", null, null, null, VatStatus.OTHER, null, null))
                .vatStatus()).isEqualTo(VatStatus.OTHER);
    }

    // ---------------------------------------------------------------------------------------
    // The step 3b obligation: a nullable VAT class override feeding the precedence rule
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the customer's VAT override is the middle level of the precedence rule")
    void vatClassOverrideFeedsPrecedence() {
        // Step 3b built VatClassPrecedence against ids because Product and Customer did not exist.
        // This is the first time the customer level is a real stored value rather than an argument.
        VatClassView reduced = vatClasses.requireByCode("1131");
        VatClassView standard = vatClasses.requireByCode("1410");

        CustomerView customer = customers.create(NewCustomer.domestic(
                "CustIT — Reduced rate customer", "EL088888003"));
        assertThat(customer.vatClassOverride())
                .as("an override is the exception, so it starts absent")
                .isEmpty();

        CustomerView overridden =
                customers.changeVatClassOverride(customer.id(), reduced.id());
        assertThat(overridden.vatClassOverride()).contains(reduced.id());

        // Customer beats product; a line beats the customer.
        VatClassResolution byCustomer = VatClassPrecedence.resolve(
                null, overridden.vatClassOverrideId(), standard.id());
        assertThat(byCustomer.source()).isEqualTo(VatClassSource.CUSTOMER);
        assertThat(byCustomer.vatClassId()).isEqualTo(reduced.id());

        // Cleared, the product's default stands again.
        assertThat(customers.changeVatClassOverride(customer.id(), null).vatClassOverride())
                .isEmpty();
        assertThat(VatClassPrecedence.resolve(null, null, standard.id()).source())
                .isEqualTo(VatClassSource.PRODUCT);
    }

    @Test
    @DisplayName("an inactive VAT class cannot be a customer override")
    void inactiveVatClassIsRefused() {
        // A class is deactivated precisely so new documents stop using it — that is how a rate
        // change is handled, rather than editing a rate in place.
        // The code fits vat_class.code's varchar(20); a longer fixture name fails on insert rather
        // than testing anything about customers.
        VatClassView retired = vatClasses.create(new NewVatClass(
                "TEST-CUST-INACTIVE", "Retired rate (test)", Rate.of("11")));
        vatClasses.deactivate(retired.id());

        CustomerView customer = customers.create(NewCustomer.domestic(
                "CustIT — Inactive override", "EL088888004"));

        assertThatExceptionOfType(InvalidCustomerException.class)
                .isThrownBy(() -> customers.changeVatClassOverride(customer.id(), retired.id()))
                .withMessageContaining("inactive");

        assertThatExceptionOfType(InvalidCustomerException.class)
                .isThrownBy(() -> customers.changeVatClassOverride(customer.id(), 999_999L))
                .withMessageContaining("No VAT class with id 999999");
    }

    // ---------------------------------------------------------------------------------------
    // Matching — brief §5's identity model, and CLAUDE.md rule 7
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the VAT number is the one match that may be applied without confirmation")
    void vatNumberIsAuthoritative() {
        CustomerView created = customers.create(NewCustomer.domestic(
                "CustIT — Authoritative match", "EL088888005"));

        assertThat(customers.findByVatNumber("EL088888005")).contains(created);
        assertThat(customers.findByVatNumber("el088888005")).contains(created);

        // Never matches on the absence of the identifier that makes it safe.
        assertThat(customers.findByVatNumber(null)).isEmpty();
        assertThat(customers.findByVatNumber(" ")).isEmpty();
    }

    @Test
    @DisplayName("email and phone are suggestive only — two people at one address stay two people")
    void emailAndPhoneAreSuggestionsOnly() {
        customers.create(NewCustomer.retail(
                "CustIT — Household A", "household@custit.example", "+30 2100000009"));
        customers.create(NewCustomer.retail(
                "CustIT — Household B", "household@custit.example", "+30 2100000009"));

        // The concrete reason brief §5 calls these suggestive: a shared household address. Merging
        // on it would attribute one person's purchases and credit balance to another.
        assertThat(customers.suggestMatches(null, "household@custit.example", null))
                .extracting(CustomerView::name)
                .contains("CustIT — Household A", "CustIT — Household B");
        assertThat(customers.suggestMatches(null, null, "+30 2100000009")).hasSizeGreaterThan(1);

        assertThat(customers.suggestMatches(null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("merging is deliberately absent until the ledger exists")
    void noMergeYet() {
        // Brief §5 specifies that a merge aliases the old id forward and never rewrites history,
        // which needs an alias table and a decision about postings already made under the retired
        // id. Half of that is worse than none: a merge that appears to work and loses references.
        assertThat(CustomerService.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("merge")
                        || method.getName().toLowerCase().contains("alias"));
    }

    @Test
    @DisplayName("a customer is deactivated, never deleted")
    void deactivateAndReactivate() {
        CustomerView created = customers.create(NewCustomer.retail(
                "CustIT — Dormant", null, null));

        customers.deactivate(created.id());
        assertThat(customers.require(created.id()).active()).isFalse();
        assertThat(customers.active()).extracting(CustomerView::id).doesNotContain(created.id());
        assertThat(customers.all()).extracting(CustomerView::id).contains(created.id());

        customers.reactivate(created.id());
        assertThat(customers.require(created.id()).active()).isTrue();
    }

    @Test
    @DisplayName("a missing customer names the id it wanted")
    void missingCustomer() {
        assertThatExceptionOfType(CustomerNotFoundException.class)
                .isThrownBy(() -> customers.require(999_999L))
                .withMessageContaining("999999");
    }

    // ---------------------------------------------------------------------------------------
    // Q10 — the generic retail customer, seeded and protected
    // ---------------------------------------------------------------------------------------
    // Step 5 deliberately seeded none, on the grounds that a catch-all absorbs every unmatched sale
    // and then cannot be untangled. Answered in step 9: seed it, and make it structural — because the
    // alternative is a person creating it by hand on day one, which produces exactly that row with
    // nothing in the software able to tell which one it is.

    @Test
    @DisplayName("Q10 — the shared retail customer is seeded and locatable by key, not by name")
    void retailCustomerIsSeeded() {
        CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);

        assertThat(retail.name()).isEqualTo("Πελάτης Λιανικής");
        assertThat(retail.isSystemRecord()).isTrue();
        assertThat(retail.active()).isTrue();
        // Not one identifiable party, so it can hold neither a VAT number nor a claim about a
        // counterparty's status.
        assertThat(retail.vatStatus()).isEqualTo(VatStatus.DOMESTIC);
        assertThat(retail.vatNumber()).isNull();
        assertThat(retail.vatExemptionReasonId()).isNull();

        // Exactly one, which is the whole point of the key: a second cannot be created through the
        // service, because nothing there sets one.
        assertThat(customers.all()).filteredOn(CustomerView::isSystemRecord).hasSize(1);
    }

    @Test
    @DisplayName("Q10 — the retail customer cannot be deactivated, and is refused by both layers")
    void retailCustomerCannotBeDeactivated() {
        CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);

        assertThatExceptionOfType(InvalidCustomerException.class)
                .isThrownBy(() -> customers.deactivate(retail.id()))
                .withMessageContaining("structural record");

        // And by CHECK, so it holds against a psql session that never came through the service.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE customer SET active = false WHERE id = ?", retail.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_system_record_stays_active");
    }

    @Test
    @DisplayName("Q10 — the retail customer's VAT treatment is fixed, by CHECK")
    void retailCustomerVatTreatmentIsFixed() {
        CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE customer SET vat_number = 'EL099999999' WHERE id = ?", retail.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_system_record_has_no_vat_number");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE customer SET vat_status = 'EXEMPT' WHERE id = ?", retail.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Q10 — it is refused on both sides of a merge, and merge still does not exist")
    void retailCustomerIsNeverMerged() {
        CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);

        // The rule is stated now so that whoever builds merge consults it rather than rediscovering
        // the argument: this is the absence of a party, so aliasing it into somebody would attribute
        // every anonymous till sale to one named person, and aliasing somebody into it would erase a
        // real customer's history into an anonymous bucket.
        assertThat(retail.isMergeable()).isFalse();
        assertThat(CustomerSystemKey.RETAIL_WALK_IN.isMergeable()).isFalse();
        assertThat(CustomerSystemKey.RETAIL_WALK_IN.isDeactivatable()).isFalse();

        // Still not built — brief §5's alias-forward needs an alias table and a rule for postings made
        // under the retired id, and half a merge loses references while appearing to work.
        assertThat(CustomerService.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase(java.util.Locale.ROOT)
                        .contains("merge"));
    }

    // ---------------------------------------------------------------------------------------
    // Enforced by the database
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the database refuses an intra-EU customer with no VAT number")
    void databaseRefusesIntraEuWithoutVatNumber() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer (name, vat_status) VALUES (?, 'INTRA_EU_B2B')
                """, "CustIT — Probe intra-EU"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_intra_eu_needs_vat_number");
    }

    @Test
    @DisplayName("the database refuses an exempt customer with no exemption reason")
    void databaseRefusesExemptWithoutReason() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer (name, vat_status) VALUES (?, 'EXEMPT')
                """, "CustIT — Probe exempt"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_exempt_needs_reason");
    }

    @Test
    @DisplayName("the database refuses two customers sharing a VAT number")
    void databaseRefusesDuplicateVatNumber() {
        jdbc.update("""
                INSERT INTO customer (name, vat_status, vat_number)
                VALUES (?, 'DOMESTIC', 'EL077777001')
                """, "CustIT — Probe VAT holder");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer (name, vat_status, vat_number)
                VALUES (?, 'DOMESTIC', 'EL077777001')
                """, "CustIT — Probe VAT duplicate"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_vat_number_unique");
    }
}
