package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.testsupport.LedgerInvariants;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

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

    @Autowired private TestRestTemplate rest;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private RouteCoverage coverage;

    private TradingQuarter quarter;

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

    // ===================================================================================
    // Coverage — reported, and reported honestly
    // ===================================================================================

    @Test
    @Order(90)
    @DisplayName("what the quarter drove, and what it did not")
    void routeCoverageIsReported() {
        // Not yet the full assertEveryRouteCoveredExcept: the narrative is January-only at this
        // point, so the excuse list would be almost the whole surface and would say nothing. What
        // this does assert is that the ledger is recording, and it prints the detail so the gap is
        // visible rather than implied.
        coverage.report().forEach(System.out::println);

        assertThat(coverage.covered())
                .as("the narrative drove routes and the ledger noticed")
                .isNotEmpty();
    }
}
