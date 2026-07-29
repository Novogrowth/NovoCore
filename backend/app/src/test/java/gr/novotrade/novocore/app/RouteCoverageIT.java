package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

/**
 * Proof that {@link RouteCoverage} counts what it claims to count.
 *
 * <p>Step 15's coverage ledger is the thing that will let the finished step say "these routes were
 * driven and these sixty-three were not" rather than "validated". That claim is worth exactly as
 * much as this class — a ledger that silently over-counts produces a coverage figure that is worse
 * than none, because it reads as complete to everyone who did not write it.
 *
 * <p>The assertion worth reading twice is {@link #aRefusedRequestIsNotCoverage()}. Step 15 sweeps
 * every route as a restricted role expecting 403, and if a refusal counted, that one sweep would
 * mark the entire surface covered.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + RouteCoverageIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + RouteCoverageIT.OWNER_PASSWORD,
        })
@Import({PostgresTestContainerConfiguration.class, RouteCoverageConfiguration.class})
@AutoConfigureTestRestTemplate
class RouteCoverageIT {

    static final String OWNER_USERNAME = "coverage.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String STAFF_USERNAME = "coverage.staff";
    private static final String STAFF_PASSWORD = "staff-password-long-enough";

    @Autowired private TestRestTemplate rest;
    @Autowired private RouteCoverage coverage;
    @Autowired private UserService users;
    @Autowired private RoleService roles;

    private ApiClient api;
    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
        coverage.reset();
    }

    @Test
    @DisplayName("the denominator is the real surface, read from Spring's own handler mapping")
    void theDenominatorIsTheRealSurface() {
        Set<RouteCoverage.Route> all = coverage.allRoutes();

        // Step 14 built 133 routes. Asserted as a floor rather than an equality, because this
        // should not fail the day a route is legitimately added — it should report it as uncovered,
        // which is the whole design.
        assertThat(all).hasSizeGreaterThan(100);
        assertThat(all).allSatisfy(route ->
                assertThat(route.pattern()).startsWith("/api/"));
        assertThat(all.stream().map(RouteCoverage.Route::toString))
                .contains("GET /api/products", "GET /api/products/{id}", "POST /api/products");
    }

    @Test
    @DisplayName("a request is recorded against its pattern, not its concrete path")
    void aRequestIsRecordedAgainstItsPattern() {
        assertThat(coverage.covered()).isEmpty();

        owner.get("/api/products");
        owner.get("/api/products/999999");   // 404, and that still exercised the route

        assertThat(coverage.covered().stream().map(RouteCoverage.Route::toString))
                .as("a 404 from the handler is a genuine exercise of the route — the refusal "
                        + "matrix depends on this being true")
                .contains("GET /api/products", "GET /api/products/{id}");
    }

    @Test
    @DisplayName("a request refused by the permission check is NOT counted as coverage")
    void aRefusedRequestIsNotCoverage() {
        ApiClient.Session staff = logInAsStaff();

        // Remote/Order Staff has no grant on the chart of accounts, so this never reaches a handler.
        assertThat(staff.get("/api/chart-of-accounts").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(coverage.covered().stream().map(RouteCoverage.Route::toString))
                .as("a 403 means the handler never ran; counting it would let one permission "
                        + "sweep mark all 133 routes covered while proving nothing")
                .doesNotContain("GET /api/chart-of-accounts");

        // And the same route, reached by someone allowed to reach it, does count — so the check
        // above is about the refusal and not about the route being unreachable in this test.
        owner.get("/api/chart-of-accounts");
        assertThat(coverage.covered().stream().map(RouteCoverage.Route::toString))
                .contains("GET /api/chart-of-accounts");
    }

    @Test
    @DisplayName("an uncovered route fails the ledger unless it is excused with a reason")
    void anUncoveredRouteMustBeExcused() {
        owner.get("/api/products");

        assertThatThrownBy(() -> coverage.assertEveryRouteCoveredExcept(Map.of()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("routes covered: 1/")
                .hasMessageContaining("GET /api/chart-of-accounts");

        // Excusing everything else passes — which is the mechanism step 15b uses for the handful
        // of routes a trading narrative genuinely cannot reach.
        Map<String, String> everythingElse = new java.util.LinkedHashMap<>();
        coverage.uncovered().forEach(route ->
                everythingElse.put(route.toString(), "not part of this probe"));
        assertThatCode(() -> coverage.assertEveryRouteCoveredExcept(everythingElse))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a stale excuse fails: for a route that does not exist, or one actually covered")
    void staleExcusesFail() {
        owner.get("/api/products");
        Map<String, String> excuses = new java.util.LinkedHashMap<>();
        coverage.uncovered().forEach(route -> excuses.put(route.toString(), "not part of this probe"));

        // An excuse naming a route nobody serves is an excuse nobody will ever remove.
        Map<String, String> withGhost = new java.util.LinkedHashMap<>(excuses);
        withGhost.put("GET /api/does-not-exist", "invented");
        assertThatThrownBy(() -> coverage.assertEveryRouteCoveredExcept(withGhost))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no such route exists");

        // And an excuse for something the scenario really did drive overstates what was skipped.
        Map<String, String> withRedundant = new java.util.LinkedHashMap<>(excuses);
        withRedundant.put("GET /api/products", "claims to be skipped, but was not");
        assertThatThrownBy(() -> coverage.assertEveryRouteCoveredExcept(withRedundant))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete the excuse");
    }

    private ApiClient.Session logInAsStaff() {
        if (users.findByUsername(STAFF_USERNAME).isEmpty()) {
            users.create(new NewUser(STAFF_USERNAME, "Coverage Staff", STAFF_PASSWORD,
                    roles.requireByName("REMOTE_ORDER_STAFF").id()));
        }
        return api.logIn(STAFF_USERNAME, STAFF_PASSWORD);
    }
}
