package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;

/**
 * <strong>Step 15c, delivered at F0: the trading quarter, run against a live server, and left there.</strong>
 *
 * <p>Step 15's proposal offered this as commit 15c and the step was agreed at full scope, which
 * included it. 15a and 15b landed; this did not, and nothing recorded that it had not — so the
 * development database this frontend is built against held nothing but Flyway's own seed, and the
 * Products screen was correctly showing an empty table. It was never wiped: the sequences behind
 * {@code product}, {@code supplier}, {@code journal_entry} and the rest had never been called once.
 *
 * <p>This is not a test of the application and it does not run in a build. It is the second driver for
 * the one scenario definition, which is what {@link HttpTransport} was extracted for. Everything it
 * asserts, it asserts because a seed pass that half-succeeded is worse than one that refused.
 *
 * <h2>Running it</h2>
 *
 * <pre>{@code
 * export NOVOCORE_SEED_USERNAME=...      # the Owner; never passed on the command line
 * export NOVOCORE_SEED_PASSWORD=...
 * cd backend
 * mvn -pl app test -Dtest=LiveSeedTest -Dsurefire.failIfNoSpecifiedTests=false \
 *     -Dnovocore.seed.base-url=https://localhost
 * }</pre>
 *
 * <h2>Three refusals, and why each one is worth the line</h2>
 *
 * <ol>
 *   <li><strong>No base URL, no run.</strong> There is deliberately no default. A default is how a
 *       seeder that was only ever meant for a laptop eventually points at something that matters, and
 *       the JUnit condition means an ordinary {@code mvn test} reports this as skipped rather than
 *       silently not having a driver at all.
 *   <li><strong>Credentials come from the environment.</strong> Not from a system property, not from a
 *       file, not from an argument — so the password does not reach a shell history, a build log, a
 *       process list or a transcript. Same standing rule the Drive refresh tokens are handled under
 *       (see {@code PROGRESS.md}'s closed credential incident).
 *   <li><strong>The target must be free of trading data.</strong> Step 15's D4 settled this: fresh
 *       only, refuse otherwise, and say why. The API deliberately has no update-or-insert semantics
 *       (step 14's D4 — commands, not CRUD), so a re-run cannot be idempotent, and a second quarter
 *       laid over the first would produce a ledger that balances and means nothing. To re-seed, run
 *       {@code docker/reset-trading-data.sql} — <strong>not</strong> {@code docker compose down -v},
 *       which on this stack also destroys the commissioned Drive credentials and the Owner account.
 * </ol>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><strong>It does not run {@code TradingQuarterOverHttpIT}'s backup and restore check.</strong> That
 * test writes {@code backup.local-directory} and the four Drive settings with stub values, which is
 * harmless in a container that is about to be thrown away and destructive against the live database:
 * it would overwrite the Drive client secrets and refresh tokens that commissioning put there and that
 * exist in no other copy. The rest of that class's checks are about the API and belong to it; the two
 * this borrows are the two that say something about the <em>data</em> rather than about the routes.
 */
@EnabledIfSystemProperty(
        named = LiveSeedTest.BASE_URL_PROPERTY,
        matches = ".+",
        disabledReason = "A seed pass needs an explicit target: -D" + LiveSeedTest.BASE_URL_PROPERTY
                + "=https://localhost. There is no default, on purpose.")
class LiveSeedTest {

    static final String BASE_URL_PROPERTY = "novocore.seed.base-url";

    private static final String USERNAME_VARIABLE = "NOVOCORE_SEED_USERNAME";
    private static final String PASSWORD_VARIABLE = "NOVOCORE_SEED_PASSWORD";

    @Test
    @DisplayName("a trading quarter is seeded into a live database, and reads back as it was written")
    void seedTheLiveDatabase() {
        LiveHttpTransport transport = new LiveHttpTransport(System.getProperty(BASE_URL_PROPERTY));
        ApiClient api = new ApiClient(transport);

        System.out.println("Seeding " + transport.baseUrl() + " as " + username() + ".");
        ApiClient.Session owner = api.logIn(username(), password());

        refuseUnlessFreeOfTradingData(owner, transport.baseUrl());

        TradingQuarter quarter = new TradingQuarter(owner);
        quarter.happens();

        // The narrative wrote it; these two say it can be read. Both are pure HTTP and already exist,
        // so the whole verification costs one constructor. JsonNumberSweep has additionally run over
        // every response above — against this build of the application rather than a test container,
        // which is the one thing this driver can check that the Failsafe one cannot.
        ReadBackChecks checks = new ReadBackChecks(owner, quarter);
        List<String> failures = new ArrayList<>();
        for (ReadBackChecks.ReadBack document : checks.documents()) {
            try {
                document.check().run();
            } catch (AssertionError failure) {
                // Collected rather than thrown, because these run after the writing is done: unlike a
                // failure mid-narrative, there is no half-written state making the next check
                // meaningless, and reporting the first of eleven would hide the other ten.
                failures.add(document.what() + " — " + failure.getMessage());
            }
        }
        assertThat(failures).as("documents that did not read back as they were written").isEmpty();
        checks.assertFiltersActuallyFilter();

        reportWhatWasLeftBehind(owner);
    }

    /**
     * The gate, asked through the API rather than of the database, because the API is the only thing
     * this driver is entitled to know about its target.
     *
     * <p><strong>Customers are not part of it, deliberately.</strong> The retail walk-in customer is
     * seeded and structural (ADR 0009 / Q10), so an empty {@code customer} table is the one state that
     * would mean something is <em>wrong</em>. Journal entries are the check that catches everything
     * else: no document in this domain exists without posting one.
     */
    private void refuseUnlessFreeOfTradingData(ApiClient.Session owner, String target) {
        List<String> found = new ArrayList<>();
        countIfAny(found, Json.items(owner.get("/api/products"), "the products").size(), "product");
        countIfAny(found, Json.items(owner.get("/api/suppliers"), "the suppliers").size(), "supplier");
        countIfAny(found, journalEntryCount(owner), "journal entry");

        if (!found.isEmpty()) {
            fail("""
                    %s already holds trading data — %s.

                    This seeds a fixed quarter and the API has no update-or-insert semantics, so a \
                    second run would lay one quarter over another rather than replace it. Reset first:

                        docker exec -i novocore-postgres-1 psql -U novocore -d novocore \
                    < docker/reset-trading-data.sql

                    That keeps Settings, Users and Roles, and the Flyway-seeded lookups. Do not use \
                    `docker compose down -v`: it also destroys the commissioned Drive credentials and \
                    the Owner account, neither of which is reproducible from docker/.env.""",
                    target, String.join(", ", found));
        }
    }

    private static void countIfAny(List<String> found, long count, String noun) {
        if (count > 0) {
            found.add(count + " " + noun + (count == 1 ? "" : "s"));
        }
    }

    private static long journalEntryCount(ApiClient.Session owner) {
        JsonNode listing = Json.ok(
                owner.get("/api/journal-entries?size=1"), "the journal listing");
        return listing.get("page").get("totalElements").asLong();
    }

    /**
     * What a person opening the frontend will now find. Printed rather than asserted: the assertions
     * that matter were made above and by the narrative itself, and a count nobody can compare against
     * anything is a display, not a check — but it is the display this step exists to produce, and it
     * is what the roadmap's fixture note should be corrected from.
     */
    private void reportWhatWasLeftBehind(ApiClient.Session owner) {
        System.out.println("""

                Seeded. What the live database now holds:
                  products         %d
                  customers        %d  (including the seeded retail walk-in)
                  suppliers        %d
                  fixed assets     %d
                  journal entries  %d
                """.formatted(
                Json.items(owner.get("/api/products"), "the products").size(),
                Json.items(owner.get("/api/customers"), "the customers").size(),
                Json.items(owner.get("/api/suppliers"), "the suppliers").size(),
                Json.items(owner.get("/api/assets"), "the assets").size(),
                journalEntryCount(owner)));
    }

    private static String username() {
        return required(USERNAME_VARIABLE);
    }

    private static String password() {
        return required(PASSWORD_VARIABLE);
    }

    private static String required(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            return fail("%s is not set. Both %s and %s are read from the environment so that the "
                            + "password does not reach a command line, a build log or a process list; "
                            + "export them in the shell that runs this and nowhere else.",
                    variable, USERNAME_VARIABLE, PASSWORD_VARIABLE);
        }
        return value;
    }
}
