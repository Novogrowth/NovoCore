package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.BackupService;
import gr.novotrade.novocore.core.api.backup.BackupView;
import gr.novotrade.novocore.core.api.backup.RestoreCheckStatus;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.backup.PostgresTools;
import gr.novotrade.novocore.core.backup.StubDriveServer;
import gr.novotrade.novocore.core.testsupport.LedgerInvariants;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * <strong>Step 15: a trading quarter driven only through HTTP, then every invariant swept.</strong>
 *
 * <p>{@code WholeScenarioIT} proves the domain is correct when driven through its services. This
 * proves the <em>REST surface</em> is a faithful and usable route to that domain — a different
 * question, and one nothing has asked. Half the 133 routes had never received an HTTP request when
 * this was written.
 *
 * <p><strong>The distinction that makes this worth its cost.</strong> A serialisation or routing
 * defect corrupts what the service layer gets right, and {@code WholeScenarioIT} structurally cannot
 * see it because it never crosses the boundary. Step 15a's first run found exactly such a defect —
 * every VAT rate crossing the wire as a JSON number — in a route four tests already called.
 *
 * <p><strong>This class gets its own database</strong>, like {@code WholeScenarioIT} and for the
 * same reason: the sweeps assert equalities over the whole ledger ("the Inventory account equals the
 * sum of what the lots carry"), which is only meaningful if nothing else put anything there.
 *
 * <p>The narrative is built once, in order, by {@link #theQuarterHappens()}. The checks are separate
 * tests reading what it left behind, because one method asserting everything reports the first
 * failure and hides the rest.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + TradingQuarterOverHttpIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + TradingQuarterOverHttpIT.OWNER_PASSWORD,
            // Its own context, therefore its own container — see the class javadoc.
            "novocore.test.scenario=trading-quarter-over-http",
        })
@Import({PostgresTestContainerConfiguration.class, RouteCoverageConfiguration.class})
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TradingQuarterOverHttpIT {

    static final String OWNER_USERNAME = "quarter.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    /** base64 of exactly 32 bytes. The same fixed key {@code BackupIT} and {@code WholeScenarioIT} use. */
    private static final String KEY = "bm92b2NvcmUtdGVzdC1iYWNrdXAta2V5LTMyYnl0ZSE=";

    /**
     * The routes this narrative deliberately does not drive, each with the reason it does not.
     *
     * <p><strong>Five, and they are all one section for one reason.</strong> The list started at
     * forty-three, and going through it was the useful part of turning coverage into an assertion:
     * almost every entry turned out not to be unreachable but merely unwritten — a supplier renamed
     * where a product had been, a settlement allocated afterwards where every other one was allocated
     * as it was recorded, a chart of accounts nothing had ever written to over HTTP. Those became
     * {@code TradingQuarter.quarterEndHousekeeping}. What is left is the set that genuinely cannot be
     * reached from a trading narrative.
     *
     * <p>The email outbox has no route that <em>sends</em> anything — by step 14's decision, sending is
     * a consequence of a document being issued and is never itself an HTTP verb. So the only way to put
     * a message in the outbox is to call {@code EmailSender} directly, and this narrative's whole claim
     * is that nothing here touches a service. Smuggling one in to raise a coverage number would trade
     * the property that makes the file worth having for a percentage.
     *
     * <p>They are covered — {@code OutboxEndpointIT} drives all five against messages it queues through
     * the service, which is the right place for it.
     */
    private static final java.util.Map<String, String> EXCUSED_ROUTES = java.util.Map.of(
            "GET /api/email/outbox",
            "no route sends email; a trading narrative cannot put a message in the outbox without "
                    + "calling EmailSender directly. Covered by OutboxEndpointIT.",
            "GET /api/email/outbox/{id}",
            "same — see GET /api/email/outbox. Covered by OutboxEndpointIT.",
            "GET /api/email/outbox/{id}/attachments",
            "same, and Q44's access path is asserted where the attachments exist: OutboxEndpointIT.",
            "GET /api/email/attachments/{id}/content",
            "same. Q44's other half — a referenced document re-checks its own record's section — is "
                    + "asserted by OutboxEndpointIT, which has a document to reference.",
            "POST /api/email/outbox/{id}/retry",
            "re-queueing needs a FAILED message, which needs a send that failed. OutboxEndpointIT "
                    + "arranges one; nothing an operator does over HTTP produces it.",
            "GET /api/me",
            "session identity, not a trading action — it says who is logged in and what they may "
                    + "reach, and nothing about the quarter. Covered by MeIT, which also asserts "
                    + "the thing that matters about it: a role granted nothing still reaches it.",
            "PATCH /api/me/language",
            "same — a display preference the backend stores and never reads (Q47b). Driving it here "
                    + "would change the owner's language mid-narrative to assert nothing. Covered "
                    + "by MeIT.");

    private static StubDriveServer drive;

    @TempDir
    static Path backupDirectory;

    @Autowired private TestRestTemplate rest;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private RouteCoverage coverage;
    @Autowired private SettingsService settings;
    @Autowired private BackupService backups;
    @Autowired private PostgresTools postgres;
    @Autowired private JdbcTemplate jdbc;

    private TradingQuarter quarter;

    @DynamicPropertySource
    static void driveAndKey(DynamicPropertyRegistry registry) throws IOException {
        drive = new StubDriveServer();
        registry.add("novocore.backup.encryption-key", () -> KEY);
        registry.add("novocore.backup.drive.token-endpoint", drive::tokenEndpoint);
        registry.add("novocore.backup.drive.api-base", drive::apiBase);
        registry.add("novocore.backup.drive.upload-base", drive::uploadBase);
    }

    // ===================================================================================
    // The quarter
    // ===================================================================================

    @Test
    @Order(1)
    @DisplayName("a trading quarter happens, entirely over HTTP")
    void theQuarterHappens() {
        ApiClient api = new ApiClient(rest);
        ApiClient.Session owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
        quarter = new TradingQuarter(owner);

        quarter.readTheLookups();

        quarter.januarySetsUpTheCatalogue();
        quarter.januaryInvoiceArrivesBeforeTheGoods();
        quarter.januaryGoodsArriveBeforeTheInvoice();
        quarter.januaryFirstSalesAndTheFreightInvoice();

        quarter.februaryTheMachinesArrive();
        quarter.februaryTheBundleAndAMachineSell();
        quarter.februarySettlesAndLeavesACredit();

        quarter.marchTheReturnsComeBack();
        quarter.marchLosesSomeStock();
        quarter.marchClosesTheQuarter();

        quarter.quarterEndReview();
        quarter.quarterEndCorrections();
        quarter.quarterEndHousekeeping();
    }

    // ===================================================================================
    // The books the API produced are correct
    // ===================================================================================

    /**
     * The headline. Not "every request returned 2xx" but <em>the books this API produced are
     * correct</em> — the same twelve invariants {@code WholeScenarioIT} asserts, asked of a database
     * that only ever received HTTP requests.
     */
    @TestFactory
    @Order(10)
    @DisplayName("the universal ledger invariants, over an HTTP-built database")
    Stream<DynamicTest> theBooksAreSound() {
        return LedgerInvariants.from(applicationContext)
                .all(TradingQuarter.JANUARY_FIRST, TradingQuarter.MARCH_LAST).stream()
                .map(invariant -> DynamicTest.dynamicTest(invariant.name(), invariant::run));
    }

    @Test
    @Order(11)
    @DisplayName("the quarter produced a substantial ledger, so the sweeps mean something")
    void theLedgerIsNotTrivial() {
        // An empty database satisfies every invariant above perfectly, so this is what stops the
        // sweep passing vacuously. Thresholds are this scenario's own claim about itself.
        LedgerInvariants invariants = LedgerInvariants.from(applicationContext);
        invariants.theLedgerIsNotTrivial(TradingQuarter.MARCH_LAST, 8L, 30L, 10);

        assertThat(invariants.sourcesPosted())
                .as("the narrative must exercise breadth, not produce many entries through one path")
                .contains("PURCHASE_INVOICE", "GOODS_RECEIPT", "SALES_INVOICE", "FREIGHT_ALLOCATION");
    }

    @Test
    @Order(20)
    @DisplayName("a listing asked for the wrong way says how to ask, rather than \"Bad request.\"")
    void parameterGuidanceReachesTheCaller() {
        // Found by the quarter-end review, which was the first thing to call these listings the way
        // a client would. Seventeen controller messages across nine controllers were being thrown
        // away, because the controllers signalled a client mistake with the exception the handler
        // treats as a programming error. A route whose only correct usage cannot be discovered from
        // its own error is one a frontend gets written against by guesswork.
        ApiClient.Session owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);

        record Case(String path, String mustSay) {
        }
        List<Case> cases = List.of(
                new Case("/api/inventory/consumptions", "productId"),
                new Case("/api/inventory/lots", "productId"),
                new Case("/api/open-items", "partyType"),
                new Case("/api/customer-credits", "customerId"),
                new Case("/api/settlements", "name a party instead"),
                new Case("/api/sales-invoices", "customerId"),
                new Case("/api/credit-notes", "customerId"),
                new Case("/api/purchase-invoices", "supplierId"),
                new Case("/api/bank-transfers", "accountId"),
                new Case("/api/goods-receipts", "supplierId"),
                new Case("/api/freight-allocations", "from"));

        for (Case listing : cases) {
            ResponseEntity<String> response = owner.get(listing.path());
            assertThat(response.getStatusCode())
                    .as("%s with no parameters", listing.path())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody())
                    .as("%s must say how to ask, not just refuse", listing.path())
                    .contains(listing.mustSay())
                    .doesNotContain("\"detail\":\"Bad request.\"");
        }
    }

    /**
     * <strong>What was written comes back.</strong> The invariant sweep above is a strong claim about
     * the <em>postings</em> and says nothing about the documents: an invoice could come back with the
     * wrong date or a missing line and every one of the twelve would still pass. These are the values
     * a user reads off a screen.
     */
    @TestFactory
    @Order(12)
    @DisplayName("every document reads back as it was written")
    Stream<DynamicTest> documentsReadBack() {
        return readBackChecks().documents().stream()
                .map(document -> DynamicTest.dynamicTest(document.what(), document.check()::run));
    }

    /**
     * <strong>{@code ?from=} and {@code ?to=} at the boundaries.</strong> Whether either bound is
     * inclusive is asserted nowhere else in this repository, and the quarter's own reads all span the
     * whole period, which cannot tell an inclusive bound from an exclusive one. A month-end report
     * that silently drops the last day's sales is wrong by an amount nobody notices.
     */
    @TestFactory
    @Order(13)
    @DisplayName("a date range includes both its ends, and excludes the days outside them")
    Stream<DynamicTest> dateRangesAreInclusiveAtBothEnds() {
        ReadBackChecks checks = readBackChecks();
        return ReadBackChecks.dateFilteredListings().stream()
                .map(listing -> DynamicTest.dynamicTest(listing.path() + " by " + listing.dateField(),
                        () -> checks.assertBoundariesAreInclusive(listing)));
    }

    @Test
    @Order(14)
    @DisplayName("a filtered list contains what belongs to it, and nothing that does not")
    void filtersActuallyFilter() {
        // The second half is the one worth having: "contains what I asked for" passes against a
        // listing that ignores its filter and returns everything, which looks like working software
        // right up until two parties' figures are compared.
        readBackChecks().assertFiltersActuallyFilter();
    }

    private ReadBackChecks readBackChecks() {
        return new ReadBackChecks(
                new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD), quarter);
    }

    /**
     * <strong>The refusal matrix.</strong> Everything above proves the API is right when it is asked
     * for something reasonable. This proves it is right when it is not — a status a client can branch
     * on, an RFC 7807 body it can parse, and a reason an operator can act on or, where the policy
     * says so, deliberately no reason at all.
     *
     * <p>A {@code @TestFactory} so each deliberate mistake reports as its own test: eighteen
     * assertions in one method would stop at the first and hide the rest, and the ones behind it are
     * the ones nobody has looked at.
     */
    @TestFactory
    @Order(30)
    @DisplayName("the refusal matrix — a bad request is refused, and says why")
    Stream<DynamicTest> badRequestsAreRefusedAndExplained() {
        ApiClient.Session owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);
        return new RefusalMatrix(owner, quarter).all().stream()
                .map(refusal -> DynamicTest.dynamicTest(refusal.what(), refusal::verify));
    }

    // ===================================================================================
    // And the quarter survives a backup and a restore
    // ===================================================================================

    /**
     * <strong>The books this API produced restore into a fresh database and still balance there.</strong>
     *
     * <p>{@code WholeScenarioIT} makes the same claim about a database built through the services.
     * This one is about a database built <em>only</em> by HTTP requests, which is the state the real
     * system will be in from step 16 onwards — so it is the copy whose restorability actually matters.
     *
     * <p>What the restore check asserts is step 12's, and it is the strong part: it really dumps, really
     * restores into a real scratch database, and asserts <strong>the restored ledger balances</strong>
     * — the difference between "the file restored" and "the books restored" — then compares row counts
     * per table against the live database and refuses to pass if any differ.
     *
     * <p><strong>What it deliberately does not do, stated rather than implied:</strong> the twelve
     * invariants are not re-run <em>inside</em> the scratch database. The verifier owns that connection
     * and drops the database when it is done, and prising it open to run a second sweep would mean
     * changing step 12's production code to suit a test. What is asserted instead is the pair that
     * brackets it — the restored copy balances and matches row for row, and the twelve invariants still
     * hold on the <em>live</em> database afterwards, so the backup cycle is shown to have left the
     * source untouched. A dump taken while the application is running, and a verifier that creates and
     * drops databases next door, are both things worth proving harmless.
     *
     * <p>Skipped without the PostgreSQL client tools, for {@code BackupIT}'s reason.
     */
    @TestFactory
    @Order(80)
    @DisplayName("the quarter backs up, restores into a fresh database, and the live books still hold")
    Stream<DynamicTest> theQuarterRestores() {
        Assumptions.assumeTrue(postgres.pgDumpVersion().isPresent(),
                "pg_dump is not on the PATH. Install postgresql-client-17 to run this; CI does.");

        settings.put("backup.local-directory", backupDirectory.toString());
        settings.put("backup.drive.primary.folder-id", "folder-trading-quarter");
        settings.put("backup.drive.primary.client-id", "client-trading-quarter");
        settings.putSecret("backup.drive.primary.client-secret", "secret-trading-quarter");
        settings.putSecret("backup.drive.primary.refresh-token", "refresh-trading-quarter");

        BackupView backup = backups.runNow();
        assertThat(backup.status()).isEqualTo(BackupRunStatus.SUCCEEDED);

        RestoreCheckView check = backups.verifyRestore(backup.id());
        assertThat(check.status())
                .as("the restore check failed: %s", check.error())
                .isEqualTo(RestoreCheckStatus.PASSED);
        assertThat(check.findings())
                .as("the check must state, in its own words, that the restored ledger balances")
                .anySatisfy(finding -> assertThat(finding).contains("restored ledger balances"));

        // And that it restored the quarter rather than an empty schema. A PASSED check on a ledger
        // this size is already the claim; reading the count back puts it in the failure message
        // instead of leaving it buried in a status enum.
        long liveEntries = jdbc.queryForObject("SELECT count(*) FROM journal_entry", Long.class);
        assertThat(liveEntries).as("the quarter posted entries to restore").isGreaterThan(30L);
        assertThat(check.findings())
                .anySatisfy(finding -> assertThat(finding)
                        .startsWith("journal_entry:")
                        .contains(String.format("%,d", liveEntries)));

        // The live database, swept again after all of that.
        return LedgerInvariants.from(applicationContext)
                .all(TradingQuarter.JANUARY_FIRST, TradingQuarter.MARCH_LAST).stream()
                .map(invariant -> DynamicTest.dynamicTest(
                        "after the backup — " + invariant.name(), invariant::run));
    }

    // ===================================================================================
    // Coverage — asserted, not merely reported
    // ===================================================================================

    /**
     * <strong>Every route in the surface was driven, or is excused here with a reason.</strong>
     *
     * <p>This is what {@link RouteCoverage} was built for, and running it last is the point: a
     * validation that covers ninety routes of a hundred and thirty-three and reports "validated" is
     * worse than one that names the forty-three it missed, because the first reads as complete
     * coverage to everyone who did not write it.
     *
     * <p>Two things make the excuse list honest rather than a place to hide. An excuse for a route
     * that does not exist fails, and so does an excuse for a route the narrative did in fact drive —
     * so the list cannot quietly outlive what it describes. And a route added in a later step is
     * uncovered, and therefore failing, the day it appears, without anyone remembering to update
     * anything.
     *
     * <p><strong>Every excuse below is a decision, and they fall into four kinds.</strong> Routes
     * whose <em>section</em> the quarter never had a reason to touch (the email outbox, which needs
     * mail this scenario does not send); routes that would <em>undo</em> the quarter and destroy the
     * ledger the invariants are asserted against (deactivations, dissolving the bundle, releasing
     * allocations); routes whose data <em>does not exist</em> in a three-month scenario (an asset's
     * depreciation rate, still pending the accountant); and the second half of paired corrections the
     * narrative exercises once (renaming a supplier when it renames a product).
     */
    @Test
    @Order(90)
    @DisplayName("every route was driven, or is excused here with a reason")
    void everyRouteIsCoveredOrExcused() {
        coverage.report().forEach(System.out::println);
        coverage.assertEveryRouteCoveredExcept(EXCUSED_ROUTES);
    }
}
