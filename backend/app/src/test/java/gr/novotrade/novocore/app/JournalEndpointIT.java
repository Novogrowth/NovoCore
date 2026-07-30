package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The journal listing: paging, filtering, and the two traps that make it worth its own test.
 *
 * <h2>What this exists to prove beyond "the filters filter"</h2>
 *
 * <ul>
 *   <li><strong>A page really is a page.</strong> {@link Memory#aPageLoadsOnlyItsOwnRows()} asserts
 *       against Hibernate's own statistics that requesting five rows loads five, not the table. That
 *       is the whole reason {@code JournalEntrySummaryView} exists, and it is the kind of decision
 *       that gets quietly undone by somebody adding a convenient {@code join fetch}.
 *   <li><strong>Successive pages neither repeat nor skip.</strong> Asserted over entries that share a
 *       date, which is the only case where it can fail — and the ordinary case in a ledger.
 *   <li><strong>The refusals carry their reason</strong>, including the two the paging contract
 *       states: a size below one and a size above the maximum.
 * </ul>
 *
 * <p>{@code TradingQuarterOverHttpIT} covers the routes against a real quarter and cross-checks the
 * summary's total against the entry's own lines. This covers the shapes that quarter has no reason to
 * produce.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + JournalEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + JournalEndpointIT.OWNER_PASSWORD,
            // Needed by the memory-shape assertion below, and harmless elsewhere.
            "spring.jpa.properties.hibernate.generate_statistics=true",
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class JournalEndpointIT {

    static final String OWNER_USERNAME = "journal.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    /** All the fixture entries share this date, so every ordering has ties to break. */
    private static final LocalDate SHARED_DATE = LocalDate.of(2026, 3, 15);

    private static final int FIXTURE_ENTRIES = 25;

    @Autowired private TestRestTemplate rest;
    @Autowired private JournalService journal;
    @Autowired private ChartOfAccountsService chart;
    @Autowired private UserService users;
    @Autowired private RoleService roles;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private ApiClient api;
    private ApiClient.Session owner;
    private long cashAccountId;
    private long salesAccountId;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
        cashAccountId = chart.requireAccount(AccountSystemKey.CASH).id();
        salesAccountId = chart.requireAccount(AccountSystemKey.SALES_STORE_AND_PHONE).id();
        givenEntries();
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("paging")
    class Paging {

        @Test
        @DisplayName("the page block reports where this page sits in the whole list")
        void pageBlockIsPresent() {
            JsonNode listing = list("?size=5");

            assertThat(listing.get("items")).hasSize(5);
            JsonNode page = listing.get("page");
            assertThat(page.get("page").asInt()).isZero();
            assertThat(page.get("size").asInt()).isEqualTo(5);
            assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(FIXTURE_ENTRIES);
            assertThat(page.get("hasNext").asBoolean()).isTrue();
            assertThat(page.get("hasPrevious").asBoolean()).isFalse();
        }

        /**
         * The defect the trailing id exists to prevent, asserted on the data that provokes it.
         *
         * <p>Every fixture entry shares one date. Ordering by that date alone leaves them all tied,
         * and PostgreSQL is free to return tied rows in a different order on each query — so page 2
         * could repeat a row from page 1 and skip another entirely. Nobody would notice: both pages
         * look plausible.
         */
        @Test
        @DisplayName("successive pages of a tied ordering neither repeat a row nor skip one")
        void pagesAreStableAcrossATiedSort() {
            // Walked by following hasNext rather than a fixed page count: a loop that stops short
            // asserts "no duplicates among the rows I happened to look at", which is a weaker claim
            // than it appears and passes against a paging bug in the pages it never reached.
            List<Long> seen = new ArrayList<>();
            int page = 0;
            boolean more = true;
            while (more) {
                JsonNode listing = list("?sort=ENTRY_DATE&direction=ASC&size=4&page=" + page);
                for (JsonNode item : listing.get("items")) {
                    seen.add(item.get("id").asLong());
                }
                more = listing.get("page").get("hasNext").asBoolean();
                page++;
                assertThat(page).as("guard against a hasNext that never goes false").isLessThan(100);
            }

            assertThat(seen)
                    .as("""
                            Every one of these entries shares a date. Without the id breaking the \
                            tie, two requests for successive pages can show one row twice and never \
                            show another — and both pages look entirely plausible.""")
                    .doesNotHaveDuplicates();

            // And the walk really did cover the fixtures, rather than passing because it saw little.
            assertThat(seen).hasSizeGreaterThanOrEqualTo(FIXTURE_ENTRIES);
        }

        @Test
        @DisplayName("descending order is the reverse of ascending, tiebreaker included")
        void directionReversesTheWholeOrdering() {
            List<Long> firstTenAscending = idsIn(list("?sort=ENTRY_DATE&direction=ASC&size=10"));
            List<Long> allDescending = idsIn(list("?sort=ENTRY_DATE&direction=DESC&size=500"));

            List<Long> reversed = new ArrayList<>(allDescending);
            java.util.Collections.reverse(reversed);

            assertThat(reversed)
                    .as("""
                            Reversing the whole descending list must reproduce the ascending one, \
                            tiebreaker included — so its first ten are the first page ascending. \
                            This is what catches a descending sort that reverses the date but \
                            leaves the id tiebreaker ascending, which produces a plausible-looking \
                            order that is not the reverse of anything.""")
                    .startsWith(firstTenAscending.toArray(new Long[0]));
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("filtering")
    class Filtering {

        @Test
        @DisplayName("by date range, both ends inclusive")
        void byDateRange() {
            assertThat(list("?from=" + SHARED_DATE + "&to=" + SHARED_DATE)
                    .get("page").get("totalElements").asLong())
                    .isGreaterThanOrEqualTo(FIXTURE_ENTRIES);

            assertThat(list("?from=" + SHARED_DATE.plusDays(1)).get("items"))
                    .as("a range that starts after every fixture entry")
                    .isEmpty();
        }

        @Test
        @DisplayName("by account — whole entries, and each appears once however many lines it has")
        void byAccount() {
            JsonNode listing = list("?accountId=" + cashAccountId + "&size=500");

            assertThat(listing.get("items")).isNotEmpty();
            assertThat(idsIn(listing))
                    .as("""
                            The account filter is an EXISTS subquery rather than a join for exactly \
                            this: an entry with two lines on the account must appear once. A join \
                            would return it twice and make the page size mean something different \
                            per row.""")
                    .doesNotHaveDuplicates();

            // Every entry returned really does touch the account.
            for (JsonNode item : listing.get("items")) {
                JournalEntryView entry = journal.requireEntry(item.get("id").asLong());
                assertThat(entry.lines())
                        .anyMatch(line -> line.accountId() == cashAccountId);
            }
        }

        @Test
        @DisplayName("by source, over all ten values rather than the six brief §6 names")
        void bySource() {
            JsonNode manual = list("?source=" + JournalSource.MANUAL_JOURNAL_ENTRY + "&size=500");
            assertThat(manual.get("items")).isNotEmpty();
            for (JsonNode item : manual.get("items")) {
                assertThat(Json.text(item, "source"))
                        .isEqualTo(JournalSource.MANUAL_JOURNAL_ENTRY.name());
            }

            // A source nothing in this fixture produced. An empty page is the right answer, and it
            // must be an empty page rather than a refusal — the value is perfectly valid.
            assertThat(list("?source=" + JournalSource.GOODS_RECEIPT).get("items")).isEmpty();
        }

        @Test
        @DisplayName("filters combine with AND")
        void filtersCombine() {
            long matching = list("?accountId=" + cashAccountId
                    + "&source=" + JournalSource.MANUAL_JOURNAL_ENTRY
                    + "&from=" + SHARED_DATE + "&to=" + SHARED_DATE + "&size=500")
                    .get("page").get("totalElements").asLong();
            assertThat(matching).isGreaterThanOrEqualTo(FIXTURE_ENTRIES);

            long contradictory = list("?accountId=" + cashAccountId
                    + "&source=" + JournalSource.GOODS_RECEIPT + "&size=500")
                    .get("page").get("totalElements").asLong();
            assertThat(contradictory)
                    .as("the same account, a source it never posted under")
                    .isZero();
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("refusing")
    class Refusing {

        @Test
        @DisplayName("a size out of range is refused carrying the bound and why it exists")
        void sizeBoundsExplainThemselves() {
            ResponseEntity<String> tooSmall = owner.get("/api/journal-entries?size=0");
            assertThat(tooSmall.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(tooSmall).get("detail").asString())
                    .isNotEqualTo("Bad request.")
                    .contains("at least one row");

            ResponseEntity<String> tooLarge = owner.get(
                    "/api/journal-entries?size=" + (PageRequest.MAX_SIZE + 1));
            assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(tooLarge).get("detail").asString())
                    .as("""
                            The bound is what makes a large list safe to expose at all, so the \
                            message says so rather than just refusing — and a caller asking for \
                            more is refused rather than quietly given the maximum, which is how a \
                            client comes to believe it has seen the whole list.""")
                    .contains("at most " + PageRequest.MAX_SIZE);
        }

        @Test
        @DisplayName("an unknown sort is refused at the boundary, naming the values that exist")
        void unknownSortIsRefused() {
            ResponseEntity<String> response = owner.get("/api/journal-entries?sort=AMOUNT");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(response).get("detail").asString())
                    .as("sorting by amount is deliberately absent — an entry's total is not a "
                            + "column — so the refusal has to say what is on offer")
                    .contains("ENTRY_DATE")
                    .contains("AMOUNT");
        }

        @Test
        @DisplayName("a backwards date range is refused rather than answered with an empty page")
        void backwardsRangeIsRefused() {
            ResponseEntity<String> response = owner.get(
                    "/api/journal-entries?from=2026-06-30&to=2026-01-01");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(response).get("detail").asString())
                    .as("an empty page would read as \"no entries in that period\" — a wrong "
                            + "answer that looks like data")
                    .contains("runs backwards");
        }

        @Test
        @DisplayName("half a sub-ledger filter is refused, naming the missing half")
        void halfASubLedgerFilterIsRefused() {
            ResponseEntity<String> response =
                    owner.get("/api/journal-entries?subLedgerType=CUSTOMER");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(response).get("detail").asString())
                    .contains("subLedgerId");
        }

        @Test
        @DisplayName("an account that does not exist is 404, not an empty page")
        void unknownAccountIsNotFound() {
            assertThat(owner.get("/api/journal-entries?accountId=999999999").getStatusCode())
                    .as("\"no such account\" and \"this account has no entries\" are different "
                            + "facts, and only one of them is the caller's mistake")
                    .isEqualTo(HttpStatus.NOT_FOUND);

            assertThat(owner.get("/api/accounts/999999999/ledger").getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("the journal is refused to a role without it, saying neither section nor level")
        void withoutTheSectionEverythingIsForbidden() {
            RoleView clerk = roles.findByName("JOURNAL_CLERK").orElseGet(() ->
                    roles.create(new NewRole("JOURNAL_CLERK", "Created by JournalEndpointIT")));
            roles.grant(clerk.id(), Section.CHART_OF_ACCOUNTS, AccessLevel.FULL);
            if (users.findByUsername("journal.clerk").isEmpty()) {
                users.create(new NewUser(
                        "journal.clerk", "Clerk", "clerk-password-long-enough", clerk.id()));
            }
            ApiClient.Session session = api.logIn("journal.clerk", "clerk-password-long-enough");

            assertThat(session.get("/api/journal-entries").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<String> ledger =
                    session.get("/api/accounts/" + cashAccountId + "/ledger");
            assertThat(ledger.getStatusCode())
                    .as("""
                            CHART_OF_ACCOUNTS is granted and JOURNAL is not. Seeing the list of \
                            accounts is close to harmless; seeing what has posted to them is every \
                            financial figure in the business — which is the whole reason the two \
                            are separate sections, and why this path is the exception in \
                            PermissionSweepIT's table.""")
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(Json.read(ledger).get("detail").asString())
                    .doesNotContain("JOURNAL")
                    .doesNotContain("VIEW");

            // And the chart itself still reads, so the refusal above is about the ledger rather
            // than about the account being unreachable.
            assertThat(session.get("/api/accounts/" + cashAccountId).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("the shape of the query")
    class Memory {

        /**
         * A page loads its own rows and nothing else — measured, not asserted by inspection.
         *
         * <h2>⚠️ What this actually catches, having been checked rather than assumed</h2>
         *
         * <p>This test was first written against the wrong theory. The received wisdom is that a
         * fetched collection makes Hibernate page <em>in memory</em> ({@code HHH000104}), loading
         * every matching row — so the test asserted "fewer than every entry was loaded". That
         * assertion passed against a deliberately introduced collection fetch, which is how the
         * theory got checked.
         *
         * <p><strong>Hibernate 7 does not do that.</strong> It applies the limit in SQL correctly and
         * then loads each row's collection separately. Measured, for a five-row page of twenty-five
         * entries:
         *
         * <pre>
         *   without a collection fetch:  5 entities loaded,  0 collection loads
         *   with one:                   15 entities loaded,  5 collection loads
         * </pre>
         *
         * <p>So the cost of getting this wrong is an <strong>N+1 per page plus every line of every
         * row</strong> — real, worth preventing, and much smaller than an out-of-memory. The
         * assertions below are written against those numbers rather than against the folklore, and
         * both fail if a collection fetch is reintroduced.
         */
        @Test
        @DisplayName("a five-row page loads five entities and no line collections")
        void aPageLoadsOnlyItsOwnRows() {
            Statistics statistics =
                    entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

            // Before measuring anything, prove the instrument works. Without this the assertions
            // below read "0 is at most 5" and pass against every possible implementation — which is
            // what an earlier version of this test did, until a probe revealed it.
            assertThat(statistics.isStatisticsEnabled())
                    .as("hibernate.generate_statistics must be on, or this test measures nothing")
                    .isTrue();

            statistics.clear();
            journal.pageOfEntries(
                    gr.novotrade.novocore.core.api.ledger.JournalEntryFilter.unfiltered(),
                    PageRequest.of(0, 5));
            long entitiesLoaded = statistics.getEntityLoadCount();
            long collectionsLoaded = statistics.getCollectionLoadCount();

            assertThat(entitiesLoaded)
                    .as("a five-row page must load something, or the count is not being collected")
                    .isPositive();
            assertThat(entitiesLoaded)
                    .as("""
                            Loaded %d entities for a five-row page. Only the five entries should be \
                            loaded; anything more means their lines came too, which is the whole \
                            reason this listing returns a summary type.""", entitiesLoaded)
                    .isLessThanOrEqualTo(5);
            assertThat(collectionsLoaded)
                    .as("""
                            %d line collections were loaded for a page that shows no lines. This is \
                            the assertion that fails if somebody adds a convenient fetch to the \
                            specification: the response stays correct and the query count per page \
                            silently becomes N+1.""", collectionsLoaded)
                    .isZero();
        }
    }

    // -------------------------------------------------------------------------------------------

    private JsonNode list(String query) {
        return Json.ok(owner.get("/api/journal-entries" + query), "GET /api/journal-entries" + query);
    }

    private static List<Long> idsIn(JsonNode listing) {
        List<Long> ids = new ArrayList<>();
        listing.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    /**
     * Entries that all share one date, so every ordering has ties for the id to break.
     *
     * <p>Manual entries rather than documents: this test is about the listing, and a manual entry is
     * the one shape that needs no product, customer or stock behind it.
     */
    private void givenEntries() {
        if (journal.pageOfEntries(
                        gr.novotrade.novocore.core.api.ledger.JournalEntryFilter.unfiltered(),
                        PageRequest.of(0, 1))
                .totalElements() >= FIXTURE_ENTRIES) {
            return;
        }
        for (int i = 0; i < FIXTURE_ENTRIES; i++) {
            Money amount = Money.of(new BigDecimal("10.00").add(new BigDecimal(i)), Money.EUR);
            journal.postManualEntry(SHARED_DATE, "Fixture entry " + i, List.of(
                    NewJournalLine.debit(cashAccountId, amount),
                    NewJournalLine.credit(salesAccountId, amount)));
        }
    }
}
