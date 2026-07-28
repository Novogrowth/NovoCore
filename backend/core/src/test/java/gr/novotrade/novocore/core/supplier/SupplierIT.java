package gr.novotrade.novocore.core.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.supplier.InvalidSupplierException;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierNotFoundException;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Suppliers, against a real PostgreSQL with the real migrations applied.
 *
 * <p>Names and VAT numbers here are prefixed so that they cannot collide with a neighbouring test
 * class's fixtures: these tests share one non-transactional database.
 */
class SupplierIT extends AbstractCoreIntegrationTest {

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private VatExemptionReasonService exemptionReasons;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a supplier round-trips and is recorded in the audit log")
    void createAndRead() {
        SupplierView created = suppliers.create(new NewSupplier(
                "SupIT — Coffee Importers", "orders@example.com", "+30 210 1234567",
                "EL099999001", VatStatus.DOMESTIC, null));

        assertThat(created.name()).isEqualTo("SupIT — Coffee Importers");
        assertThat(created.vatNumberIfAny()).contains("EL099999001");
        assertThat(created.vatStatus()).isEqualTo(VatStatus.DOMESTIC);
        assertThat(created.active()).isTrue();

        SupplierView read = suppliers.require(created.id());
        assertThat(read.emailIfAny()).contains("orders@example.com");
        assertThat(read.phoneIfAny()).contains("+30 210 1234567");

        assertThat(auditLog.findForEntity("Supplier", String.valueOf(created.id()), 10))
                .extracting(AuditEntry::action)
                .contains("supplier.created");
    }

    @Test
    @DisplayName("no external system reference id exists anywhere on the table (rule 2)")
    void noExternalSystemIds() {
        // CLAUDE.md rule 2 asserted against the schema rather than trusted to review. A Go or Woo
        // id added here "because it is convenient" is exactly the coupling the adapters' own
        // mapping tables exist to prevent.
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'supplier'
                """, String.class))
                .noneSatisfy(column -> assertThat(column.toLowerCase())
                        .containsAnyOf("go_", "woo", "external", "skroutz", "acs"));
    }

    // ---------------------------------------------------------------------------------------
    // VAT status coherence — the rules that make a status true rather than merely recorded
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("an intra-EU B2B supplier without a VAT number is refused, with the reason")
    void intraEuNeedsVatNumber() {
        assertThatExceptionOfType(InvalidSupplierException.class)
                .isThrownBy(() -> suppliers.create(new NewSupplier(
                        "SupIT — EU no VAT number", null, null, null,
                        VatStatus.INTRA_EU_B2B, null)))
                .withMessageContaining("without a VAT number")
                .withMessageContaining("reverse charge");
    }

    @Test
    @DisplayName("an exempt supplier must name the article it is exempt under")
    void exemptNeedsReason() {
        assertThatExceptionOfType(InvalidSupplierException.class)
                .isThrownBy(() -> suppliers.create(new NewSupplier(
                        "SupIT — Exempt no reason", null, null, null, VatStatus.EXEMPT, null)))
                .withMessageContaining("exemption reason");

        // With a real seeded AADE reason it is accepted.
        long reasonId = exemptionReasons.requireByCode(27).id();
        SupplierView exempt = suppliers.create(new NewSupplier(
                "SupIT — Exempt with reason", null, null, null, VatStatus.EXEMPT, reasonId));

        assertThat(exempt.vatExemptionReason()).contains(reasonId);
    }

    @Test
    @DisplayName("an unknown exemption reason is refused by name, not by integrity violation")
    void unknownExemptionReasonIsRefused() {
        assertThatExceptionOfType(InvalidSupplierException.class)
                .isThrownBy(() -> suppliers.create(new NewSupplier(
                        "SupIT — Bad reason", null, null, null, VatStatus.EXEMPT, 999_999L)))
                .withMessageContaining("No VAT exemption reason with id 999999");
    }

    @Test
    @DisplayName("clearing a VAT number is refused when the status depends on having one")
    void clearingVatNumberRespectsStatus() {
        SupplierView intraEu = suppliers.create(new NewSupplier(
                "SupIT — Italian roaster", null, null, "IT099999002",
                VatStatus.INTRA_EU_B2B, null));

        assertThatExceptionOfType(InvalidSupplierException.class)
                .isThrownBy(() -> suppliers.changeVatNumber(intraEu.id(), null))
                .withMessageContaining("without a VAT number");

        // Unchanged by the refused call.
        assertThat(suppliers.require(intraEu.id()).vatNumberIfAny()).contains("IT099999002");
    }

    @Test
    @DisplayName("non-EU export is its own status, not folded into OTHER")
    void nonEuExportIsDistinct() {
        // An export and an intra-EU B2B supply are both VAT-free but under different articles, so
        // they are reported differently. Collapsing them would lose exactly what has to be stated.
        SupplierView export = suppliers.create(new NewSupplier(
                "SupIT — Brazilian farm", null, null, null, VatStatus.NON_EU_EXPORT, null));

        assertThat(export.vatStatus()).isEqualTo(VatStatus.NON_EU_EXPORT);
        assertThat(export.vatNumberIfAny())
                .as("a non-EU supplier need not have anything resembling an EU VAT number")
                .isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Matching — certain versus suggested (CLAUDE.md rule 7)
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a VAT number matches exactly and is the only lookup safe to apply automatically")
    void vatNumberIsTheCertainMatch() {
        SupplierView created = suppliers.create(new NewSupplier(
                "SupIT — Match by VAT", null, null, "EL099999003", VatStatus.DOMESTIC, null));

        assertThat(suppliers.findByVatNumber("EL099999003")).contains(created);
        assertThat(suppliers.findByVatNumber("el099999003"))
                .as("case-insensitive: an imported VAT number may be capitalised differently")
                .contains(created);

        // A blank VAT number must not match "the first supplier without one", which would be an
        // automatic match on the absence of the identifier that makes automatic matching safe.
        assertThat(suppliers.findByVatNumber(null)).isEmpty();
        assertThat(suppliers.findByVatNumber("   ")).isEmpty();
    }

    @Test
    @DisplayName("a duplicate VAT number is refused, since it is the authoritative identifier")
    void duplicateVatNumberIsRefused() {
        suppliers.create(new NewSupplier(
                "SupIT — First VAT holder", null, null, "EL099999004",
                VatStatus.DOMESTIC, null));

        assertThatExceptionOfType(InvalidSupplierException.class)
                .isThrownBy(() -> suppliers.create(new NewSupplier(
                        "SupIT — Second VAT holder", null, null, "EL099999004",
                        VatStatus.DOMESTIC, null)))
                .withMessageContaining("authoritative identifier");
    }

    @Test
    @DisplayName("email and phone produce suggestions, and never pick one")
    void emailAndPhoneAreSuggestionsOnly() {
        suppliers.create(new NewSupplier(
                "SupIT — Shared office A", "shared@supit.example", "+30 2100000001",
                "EL099999005", VatStatus.DOMESTIC, null));
        suppliers.create(new NewSupplier(
                "SupIT — Shared office B", "shared@supit.example", "+30 2100000001",
                "EL099999006", VatStatus.DOMESTIC, null));

        // Two suppliers behind one switchboard is ordinary, and the point of rule 7: a match here
        // is evidence, so both come back and the caller confirms one.
        assertThat(suppliers.suggestMatches(null, "shared@supit.example", null))
                .extracting(SupplierView::name)
                .contains("SupIT — Shared office A", "SupIT — Shared office B");

        assertThat(suppliers.suggestMatches(null, null, "+30 2100000001")).hasSizeGreaterThan(1);
        assertThat(suppliers.suggestMatches("SupIT — Shared office", null, null))
                .hasSizeGreaterThan(1);

        // Nothing to go on returns nothing rather than everybody — a suggestion list containing
        // the whole table is not a suggestion.
        assertThat(suppliers.suggestMatches(null, null, null)).isEmpty();
        assertThat(suppliers.suggestMatches("  ", " ", "")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Changes
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a duplicate name is refused, and renaming to the same name is not a duplicate")
    void duplicateNameIsRefused() {
        SupplierView created = suppliers.create(NewSupplier.domestic(
                "SupIT — Unique name", "EL099999007"));

        assertThatExceptionOfType(InvalidSupplierException.class)
                .isThrownBy(() -> suppliers.create(NewSupplier.domestic(
                        "supit — unique name", "EL099999008")))
                .withMessageContaining("already exists");

        assertThat(suppliers.rename(created.id(), "SupIT — Unique name").name())
                .isEqualTo("SupIT — Unique name");
    }

    @Test
    @DisplayName("reclassifying for VAT is audited with both the old and new status")
    void changeVatStatusIsAudited() {
        SupplierView created = suppliers.create(NewSupplier.domestic(
                "SupIT — Reclassified", "DE099999009"));

        suppliers.changeVatStatus(created.id(), VatStatus.INTRA_EU_B2B, null);

        assertThat(suppliers.require(created.id()).vatStatus())
                .isEqualTo(VatStatus.INTRA_EU_B2B);
        assertThat(auditLog.findForEntity("Supplier", String.valueOf(created.id()), 10))
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo("supplier.vat-status-changed");
                    assertThat(entry.detail())
                            .containsEntry("from", "DOMESTIC")
                            .containsEntry("to", "INTRA_EU_B2B");
                });
    }

    @Test
    @DisplayName("a supplier is deactivated, never deleted")
    void deactivateAndReactivate() {
        SupplierView created = suppliers.create(NewSupplier.domestic(
                "SupIT — Retired", "EL099999010"));

        suppliers.deactivate(created.id());
        assertThat(suppliers.require(created.id()).active()).isFalse();
        assertThat(suppliers.active()).extracting(SupplierView::id).doesNotContain(created.id());
        // Still readable: purchase history posted against them must remain explicable.
        assertThat(suppliers.all()).extracting(SupplierView::id).contains(created.id());

        // Idempotent rather than throwing.
        suppliers.deactivate(created.id());

        suppliers.reactivate(created.id());
        assertThat(suppliers.require(created.id()).active()).isTrue();

        assertThat(SupplierService.class.getMethods())
                .as("there is no delete anywhere in the core")
                .noneMatch(method -> method.getName().toLowerCase().contains("delete")
                        || method.getName().toLowerCase().contains("remove"));
    }

    @Test
    @DisplayName("a missing supplier names the id it wanted")
    void missingSupplier() {
        assertThatExceptionOfType(SupplierNotFoundException.class)
                .isThrownBy(() -> suppliers.require(999_999L))
                .withMessageContaining("999999");

        assertThat(suppliers.find(999_999L)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Enforced by the database, not only by Java
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the database refuses an intra-EU supplier with no VAT number")
    void databaseRefusesIntraEuWithoutVatNumber() {
        // Asserted against raw SQL on purpose: a rule that exists only in Java says nothing about
        // a psql session or a future migration.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO supplier (name, vat_status) VALUES (?, 'INTRA_EU_B2B')
                """, "SupIT — Probe intra-EU"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("supplier_intra_eu_needs_vat_number");
    }

    @Test
    @DisplayName("the database refuses an exempt supplier with no exemption reason")
    void databaseRefusesExemptWithoutReason() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO supplier (name, vat_status) VALUES (?, 'EXEMPT')
                """, "SupIT — Probe exempt"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("supplier_exempt_needs_reason");
    }

    @Test
    @DisplayName("the database refuses an unknown VAT status")
    void databaseRefusesUnknownVatStatus() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO supplier (name, vat_status) VALUES (?, 'REVERSE_CHARGE')
                """, "SupIT — Probe bad status"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("supplier_vat_status_known");
    }

    @Test
    @DisplayName("a blank VAT number is refused, so \"none\" has one representation")
    void databaseRefusesBlankVatNumber() {
        // Without this, two suppliers could both carry '' and collide on the unique index for a
        // reason nobody would guess from the error — the same trap account.code avoids.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO supplier (name, vat_status, vat_number)
                VALUES (?, 'DOMESTIC', '   ')
                """, "SupIT — Probe blank VAT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("supplier_vat_number_not_blank");
    }

    @Test
    @DisplayName("audit columns are populated on a supplier created through the service")
    void auditColumnsArePopulated() {
        SupplierView created = suppliers.create(NewSupplier.domestic(
                "SupIT — Audit columns", "EL099999011"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM supplier
                WHERE id = ? AND created_at IS NOT NULL AND btrim(created_by) <> ''
                """, Integer.class, created.id()))
                .isEqualTo(1);
    }
}
