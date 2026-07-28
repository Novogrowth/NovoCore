package gr.novotrade.novocore.core.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.asset.AssetNotFoundException;
import gr.novotrade.novocore.core.api.asset.AssetService;
import gr.novotrade.novocore.core.api.asset.AssetStatus;
import gr.novotrade.novocore.core.api.asset.AssetView;
import gr.novotrade.novocore.core.api.asset.InvalidAssetException;
import gr.novotrade.novocore.core.api.asset.NewAsset;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The fixed asset register, against a real PostgreSQL.
 *
 * <p>The register only — there is no depreciation run and no disposal posting, because both write
 * journal entries and the journal arrives in step 7. What these tests establish is that the register
 * a run will read is complete and refuses to be guessed at.
 */
class AssetIT extends AbstractCoreIntegrationTest {

    private static final LocalDate ACQUIRED = LocalDate.of(2026, 1, 20);

    @Autowired
    private AssetService assets;

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("an asset round-trips and is recorded in the audit log")
    void createAndRead() {
        AssetView created = assets.create(new NewAsset(
                "AssetIT-001", "AssetIT — Probat roaster", ACQUIRED,
                new BigDecimal("10"), null));

        assertThat(created.name()).isEqualTo("AssetIT — Probat roaster");
        assertThat(created.codeIfAny()).contains("AssetIT-001");
        assertThat(created.status()).isEqualTo(AssetStatus.IN_USE);
        assertThat(created.depreciationRate()).isPresent();
        assertThat(created.canDepreciate()).isTrue();
        assertThat(created.effectiveDepreciationStartDate()).isEqualTo(ACQUIRED);

        assertThat(assets.findByCode("assetit-001"))
                .as("code lookup is case-insensitive")
                .isPresent();
        assertThat(auditLog.findForEntity("Asset", String.valueOf(created.id()), 10))
                .extracting(AuditEntry::action).contains("asset.created");
    }

    // ---------------------------------------------------------------------------------------
    // No monetary fields — cost comes from the ledger
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the asset table holds no cost, depreciation or carrying value")
    void noMonetaryColumns() {
        // Both fixed-asset control accounts declare ASSET as their sub-ledger, so every posting to
        // them names its asset and both figures are sums of journal lines. A stored acquisition
        // cost would be a second copy of a number the ledger holds, free to drift from it after the
        // first correcting entry — the same argument that keeps a running balance off Account.
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'asset'
                """, String.class))
                .doesNotContain("acquisition_cost", "cost", "accumulated_depreciation",
                        "carrying_value", "net_book_value", "salvage_value", "residual_value",
                        // Derivable as 100/rate for straight-line, so storing it invites the two
                        // to disagree.
                        "useful_life_years", "useful_life",
                        // Straight-line only (brief §5): a single-valued column is dead weight.
                        "depreciation_method");
    }

    @Test
    @DisplayName("the two control accounts this register sits behind exist and expect it")
    void controlAccountsAreInPlace() {
        // The register is only meaningful because those accounts declare an ASSET sub-ledger; that
        // is what makes every posting name its asset, and therefore what makes cost derivable.
        AccountView atCost = chartOfAccounts.requireAccount(AccountSystemKey.FIXED_ASSETS_AT_COST);
        AccountView accumulated = chartOfAccounts.requireAccount(
                AccountSystemKey.FIXED_ASSETS_ACCUMULATED_DEPRECIATION);

        assertThat(atCost.kind()).isEqualTo(AccountKind.CONTROL);
        assertThat(atCost.subLedger()).contains(SubLedgerType.ASSET);
        assertThat(atCost.requiresSubLedgerReference()).isTrue();
        assertThat(accumulated.subLedger()).contains(SubLedgerType.ASSET);
        assertThat(accumulated.requiresSubLedgerReference()).isTrue();
    }

    // ---------------------------------------------------------------------------------------
    // Q12 — a manually set rate, and the fact that it is not known yet
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("an asset can exist without a rate, and is listed as awaiting one")
    void assetsAwaitingARate() {
        // The state the register is genuinely in: the statutory rates per category have not been
        // supplied by the accountant. Recording the asset is right; charging depreciation against a
        // guessed rate is not, and a run must skip and report these rather than substitute one.
        AssetView awaiting = assets.create(NewAsset.awaitingRate(
                "AssetIT — Grinder awaiting rate", ACQUIRED));

        assertThat(awaiting.depreciationRate()).isEmpty();
        assertThat(awaiting.canDepreciate()).isFalse();

        assertThat(assets.withoutDepreciationRate()).extracting(AssetView::id)
                .as("so \"we never filled in the rates\" is a question the system can answer")
                .contains(awaiting.id());
        assertThat(assets.depreciable()).extracting(AssetView::id)
                .doesNotContain(awaiting.id());
    }

    @Test
    @DisplayName("setting a rate moves an asset from awaiting-a-rate to depreciable, and back")
    void rateCanBeSetAndCleared() {
        AssetView asset = assets.create(NewAsset.awaitingRate(
                "AssetIT — Rate to be set", ACQUIRED));

        assets.changeDepreciationRate(asset.id(), new BigDecimal("20"));
        assertThat(assets.depreciable()).extracting(AssetView::id).contains(asset.id());
        assertThat(assets.require(asset.id()).annualMultiplier()).isEqualByComparingTo("0.20");

        // Clearing is permitted, and means "this rate was wrong and the right one is not yet
        // known" — a more honest state than a figure somebody guessed.
        assets.changeDepreciationRate(asset.id(), null);
        assertThat(assets.require(asset.id()).canDepreciate()).isFalse();
        assertThat(assets.withoutDepreciationRate()).extracting(AssetView::id)
                .contains(asset.id());

        assertThat(auditLog.findForEntity("Asset", String.valueOf(asset.id()), 10))
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo("asset.depreciation-rate-changed");
                    assertThat(entry.detail()).containsEntry("from", "(not set)");
                });
    }

    @Test
    @DisplayName("a rate written as a fraction is refused rather than accepted as 0.1%")
    void fractionalRateIsRefused() {
        // The mistake a plain 0-100 range cannot catch: 0.1 meaning 10% is inside it.
        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.create(new NewAsset(
                        null, "AssetIT — Fractional rate", ACQUIRED,
                        new BigDecimal("0.1"), null)))
                .withMessageContaining("hundred times too slowly");

        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.create(new NewAsset(
                        null, "AssetIT — Zero rate", ACQUIRED, BigDecimal.ZERO, null)))
                .withMessageContaining("between 1 and 100");

        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.create(new NewAsset(
                        null, "AssetIT — Over 100", ACQUIRED, new BigDecimal("150"), null)))
                .withMessageContaining("between 1 and 100");
    }

    @Test
    @DisplayName("no reference table of statutory rates exists yet, and none was invented")
    void noGuessedRateTable() {
        // The rates and the category taxonomy they attach to both come from the accountant, the way
        // the VAT class list did. Inventing either would be the mistake step 3b avoided by leaving
        // vat_exemption_reason empty until the real list arrived.
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                """, String.class))
                .doesNotContain("asset_category", "depreciation_rate", "asset_class");
    }

    // ---------------------------------------------------------------------------------------
    // Dates and disposal
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("depreciation can start later than acquisition, but never earlier")
    void depreciationStartDate() {
        LocalDate inService = ACQUIRED.plusMonths(2);
        AssetView asset = assets.create(new NewAsset(
                null, "AssetIT — Installed later", ACQUIRED, new BigDecimal("10"), inService));

        // Bought in one period, placed in service in another: charging from the invoice date would
        // put the depreciation in the wrong period.
        assertThat(asset.effectiveDepreciationStartDate()).isEqualTo(inService);

        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.changeDepreciationStartDate(
                        asset.id(), ACQUIRED.minusDays(1)))
                .withMessageContaining("before the asset was acquired");

        // Cleared, it falls back to the acquisition date.
        assets.changeDepreciationStartDate(asset.id(), null);
        assertThat(assets.require(asset.id()).effectiveDepreciationStartDate())
                .isEqualTo(ACQUIRED);
    }

    @Test
    @DisplayName("disposal stops depreciation, and a second disposal is refused not overwritten")
    void disposal() {
        AssetView asset = assets.create(new NewAsset(
                null, "AssetIT — Sold machine", ACQUIRED, new BigDecimal("10"), null));
        LocalDate sold = ACQUIRED.plusYears(2);

        AssetView disposed = assets.dispose(asset.id(), sold);

        assertThat(disposed.status()).isEqualTo(AssetStatus.DISPOSED);
        assertThat(disposed.disposal()).contains(sold);
        assertThat(disposed.depreciationRate())
                .as("the rate is still recorded; it is the status that stops the charge")
                .isPresent();
        assertThat(disposed.canDepreciate()).isFalse();
        assertThat(assets.depreciable()).extracting(AssetView::id).doesNotContain(asset.id());
        assertThat(assets.inUse()).extracting(AssetView::id).doesNotContain(asset.id());
        // Still readable: postings made against it must remain explicable.
        assertThat(assets.all()).extracting(AssetView::id).contains(asset.id());

        // A second disposal date would silently move the period the asset left in, which is the
        // period its gain or loss belongs to.
        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.dispose(asset.id(), sold.plusMonths(1)))
                .withMessageContaining("already disposed of")
                .withMessageContaining("Reinstate it first");

        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.dispose(
                        assets.create(NewAsset.awaitingRate("AssetIT — Backdated", ACQUIRED)).id(),
                        ACQUIRED.minusDays(1)))
                .withMessageContaining("before it was acquired");
    }

    @Test
    @DisplayName("a disposal recorded in error can be undone, since there is no delete")
    void reinstate() {
        AssetView asset = assets.create(NewAsset.awaitingRate(
                "AssetIT — Wrongly disposed", ACQUIRED));
        assets.dispose(asset.id(), ACQUIRED.plusMonths(6));

        AssetView reinstated = assets.reinstate(asset.id());

        assertThat(reinstated.status()).isEqualTo(AssetStatus.IN_USE);
        assertThat(reinstated.disposal()).isEmpty();
        assertThat(assets.inUse()).extracting(AssetView::id).contains(asset.id());

        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.reinstate(asset.id()))
                .withMessageContaining("no disposal to undo");

        assertThat(auditLog.findForEntity("Asset", String.valueOf(asset.id()), 10))
                .extracting(AuditEntry::action)
                .contains("asset.disposed", "asset.reinstated");
    }

    @Test
    @DisplayName("a duplicate asset code is refused; no code at all is fine")
    void codeIsUniqueWhenPresent() {
        assets.create(new NewAsset("AssetIT-DUP", "AssetIT — First tag", ACQUIRED, null, null));

        assertThatExceptionOfType(InvalidAssetException.class)
                .isThrownBy(() -> assets.create(new NewAsset(
                        "assetit-dup", "AssetIT — Second tag", ACQUIRED, null, null)))
                .withMessageContaining("already exists");

        assertThat(assets.create(NewAsset.awaitingRate("AssetIT — Untagged", ACQUIRED))
                .codeIfAny()).isEmpty();
        assertThat(assets.findByCode(null)).isEmpty();
    }

    @Test
    @DisplayName("a missing asset names the id it wanted")
    void missingAsset() {
        assertThatExceptionOfType(AssetNotFoundException.class)
                .isThrownBy(() -> assets.require(999_999L))
                .withMessageContaining("999999");
    }

    // ---------------------------------------------------------------------------------------
    // Enforced by the database
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the database refuses a disposed asset with no disposal date, and the reverse")
    void databaseEnforcesDisposalBiconditional() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, status)
                VALUES (?, DATE '2026-01-20', 'DISPOSED')
                """, "AssetIT — Probe disposed no date"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_disposed_iff_disposal_date");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, status, disposal_date)
                VALUES (?, DATE '2026-01-20', 'IN_USE', DATE '2026-06-01')
                """, "AssetIT — Probe in-use with date"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_disposed_iff_disposal_date");
    }

    @Test
    @DisplayName("the database refuses a rate outside 1-100 and a date before acquisition")
    void databaseEnforcesRateAndDates() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, depreciation_rate_percent)
                VALUES (?, DATE '2026-01-20', 0)
                """, "AssetIT — Probe zero rate"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_depreciation_rate_is_a_percentage");

        // The lower bound in the database too, not only in Java: a fraction inserted by hand or by
        // a future migration must fail the same way.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, depreciation_rate_percent)
                VALUES (?, DATE '2026-01-20', 0.100000)
                """, "AssetIT — Probe fractional rate"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_depreciation_rate_is_a_percentage");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, status, disposal_date)
                VALUES (?, DATE '2026-01-20', 'DISPOSED', DATE '2025-12-31')
                """, "AssetIT — Probe backdated disposal"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_disposal_not_before_acquisition");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, depreciation_start_date)
                VALUES (?, DATE '2026-01-20', DATE '2025-12-31')
                """, "AssetIT — Probe early depreciation"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_depreciation_start_not_before_acquisition");
    }

    @Test
    @DisplayName("the database refuses an unknown status")
    void databaseRefusesUnknownStatus() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, status)
                VALUES (?, DATE '2026-01-20', 'FULLY_DEPRECIATED')
                """, "AssetIT — Probe bad status"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asset_status_known");
    }
}
