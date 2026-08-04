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
import tools.jackson.databind.JsonNode;
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
    /**
     * Routes a trading quarter legitimately does not drive, each naming the test that does.
     *
     * <p>{@code Map.ofEntries} rather than {@code Map.of}, which caps at ten pairs — step 16b's
     * administration surface took this past it.
     *
     * <p><strong>Administration is not trading, and pretending otherwise would weaken both.</strong>
     * A quarter of invoicing has no business creating users or editing roles; driving them here to
     * satisfy a coverage count would put irrelevant state in the middle of a ledger narrative whose
     * whole value is that its twelve invariants hold over exactly the documents it wrote.
     */
    private static final java.util.Map<String, String> EXCUSED_ROUTES = java.util.Map.ofEntries(
            // ===================================================================================
            // R1a — document reference data. Fifty-four routes, all driven by
            // DocumentReferenceDataEndpointIT against the real server.
            //
            // ⚠️ These are excused as a GROUP with one argument, which is the honest shape here:
            // the quarter is a TRADING narrative and every one of these is REFERENCE DATA it does
            // not touch. Two of the tables it would touch — document types and series — ship empty
            // by design, and R1b is the step that makes a document type mandatory on a sales
            // invoice. Excusing them individually with the same sentence would look like fifty-four
            // decisions when it is one.
            // ===================================================================================
            java.util.Map.entry("DELETE /api/purchase-document-series/{id}/transformation-target",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("DELETE /api/purchase-document-types/{id}/aade-invoice-type",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("DELETE /api/sales-document-series/{id}/channel",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("DELETE /api/sales-document-series/{id}/transformation-target",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("DELETE /api/sales-document-types/{id}/aade-invoice-type",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/aade-invoice-types",
                    "the AADE codification. A trading narrative reads document types; it has no reason to administer AADE's own list, and describing or deactivating a statutory code mid-quarter would change what the quarter's own documents refer to. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/aade-invoice-types/{id}",
                    "the AADE codification. A trading narrative reads document types; it has no reason to administer AADE's own list, and describing or deactivating a statutory code mid-quarter would change what the quarter's own documents refer to. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/delivery-methods",
                    "R1a reference data, shipped empty, and nothing in the quarter records a delivery. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/delivery-methods/{id}",
                    "R1a reference data, shipped empty, and nothing in the quarter records a delivery. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/purchase-document-series",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/purchase-document-series/{id}",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/purchase-document-types",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/purchase-document-types/drafts",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/purchase-document-types/{id}",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/sales-document-series",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/sales-document-series/{id}",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/sales-document-types",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/sales-document-types/drafts",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("GET /api/sales-document-types/{id}",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/aade-invoice-types/{id}/description",
                    "the AADE codification. A trading narrative reads document types; it has no reason to administer AADE's own list, and describing or deactivating a statutory code mid-quarter would change what the quarter's own documents refer to. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/delivery-methods/{id}/description",
                    "R1a reference data, shipped empty, and nothing in the quarter records a delivery. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/purchase-document-series/{id}/description",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/purchase-document-types/{id}/description",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/sales-document-series/{id}/description",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/sales-document-types/{id}/description",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PATCH /api/vat-exemption-reasons/{id}/description",
                    "the exemption reasons became a StatutoryCodification in R1a and gained the three write operations that contract permits. The quarter READS them — its February intra-EU sale needs one — and must not edit the list it is reading. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/aade-invoice-types/{id}/deactivate",
                    "the AADE codification. A trading narrative reads document types; it has no reason to administer AADE's own list, and describing or deactivating a statutory code mid-quarter would change what the quarter's own documents refer to. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/aade-invoice-types/{id}/reactivate",
                    "the AADE codification. A trading narrative reads document types; it has no reason to administer AADE's own list, and describing or deactivating a statutory code mid-quarter would change what the quarter's own documents refer to. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/delivery-methods/{id}/deactivate",
                    "R1a reference data, shipped empty, and nothing in the quarter records a delivery. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/delivery-methods/{id}/reactivate",
                    "R1a reference data, shipped empty, and nothing in the quarter records a delivery. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/purchase-document-series/{id}/deactivate",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/purchase-document-series/{id}/reactivate",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/purchase-document-types/{id}/deactivate",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/purchase-document-types/{id}/reactivate",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/sales-document-series/{id}/deactivate",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/sales-document-series/{id}/reactivate",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/sales-document-types/{id}/deactivate",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/sales-document-types/{id}/reactivate",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/vat-exemption-reasons/{id}/deactivate",
                    "the exemption reasons became a StatutoryCodification in R1a and gained the three write operations that contract permits. The quarter READS them — its February intra-EU sale needs one — and must not edit the list it is reading. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("POST /api/vat-exemption-reasons/{id}/reactivate",
                    "the exemption reasons became a StatutoryCodification in R1a and gained the three write operations that contract permits. The quarter READS them — its February intra-EU sale needs one — and must not edit the list it is reading. Covered by DocumentReferenceDataEndpointIT."),
            // ===================================================================================
            // R2 — the seven "editable while unused" correction routes.
            //
            // ⚠️ Excused for a DIFFERENT reason from R1a's block above, and the difference matters.
            // Those are reference data a trading narrative does not touch. THESE are refused
            // outright once a document has been recorded in the series — which is precisely the
            // state this quarter spends ten invoices creating. Driving them here would either
            // assert the refusal (a reference-data rule proved inside a ledger narrative, where a
            // reader cannot see why) or require correcting a series before the quarter trades,
            // which is R2ReferenceDataContractIT's job against the real server.
            //
            // Both halves are covered there: the correction succeeds while the series is unused,
            // and is refused with its reason once an invoice names it.
            // ===================================================================================
            java.util.Map.entry("PATCH /api/delivery-methods/{id}/abbreviation",
                    "R2's correction path. Nothing in the quarter records a delivery. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PATCH /api/purchase-document-series/{id}/abbreviation",
                    "R2's correction path, purchase side. ⚠️ Its refusal cannot fire at all until F6 — nothing can name a purchase series. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PATCH /api/sales-document-series/{id}/abbreviation",
                    "R2's correction path. Refused once a document is recorded in the series, which is the state this quarter creates. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PUT /api/purchase-document-series/{id}/document-type",
                    "R2's correction path, purchase side. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PUT /api/purchase-document-series/{id}/gets-mark",
                    "R2's correction path, purchase side. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PUT /api/sales-document-series/{id}/document-type",
                    "R2's correction path. Refused once a document is recorded in the series. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PUT /api/sales-document-series/{id}/gets-mark",
                    "R2's correction path. Refused once a document is recorded in the series. Covered by R2ReferenceDataContractIT."),
            // ===================================================================================
            // R2b — four sort-code routes and the six payment-method routes.
            //
            // ⚠️ The sort code is ORDERING, not behaviour: it appears on no document, is transmitted
            // nowhere, and changes nothing about what a quarter of trading posts. Reordering a
            // reference list mid-narrative would be noise in a ledger story.
            //
            // Payment methods are the same reference-data argument as delivery methods above, with
            // one addition: the quarter READS them — every invoice it records names a settlement
            // method, and R2b refuses a deactivated one — so deactivating one here would change
            // what the narrative's own documents can be settled by. Covered by PaymentMethodIT and
            // R2ReferenceDataContractIT.
            // ===================================================================================
            java.util.Map.entry("PATCH /api/purchase-document-series/{id}/sort-code",
                    "R2b ordering only; changes nothing a quarter posts. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PATCH /api/purchase-document-types/{id}/sort-code",
                    "R2b ordering only; changes nothing a quarter posts. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PATCH /api/sales-document-series/{id}/sort-code",
                    "R2b ordering only; changes nothing a quarter posts. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("PATCH /api/sales-document-types/{id}/sort-code",
                    "R2b ordering only; changes nothing a quarter posts. Covered by R2ReferenceDataContractIT."),
            java.util.Map.entry("GET /api/payment-methods",
                    "R2b reference data. The quarter names settlement methods on every invoice but must not administer the list it is reading. Covered by PaymentMethodIT."),
            java.util.Map.entry("GET /api/payment-methods/{method}",
                    "R2b reference data. Covered by PaymentMethodIT."),
            java.util.Map.entry("PATCH /api/payment-methods/{method}/description",
                    "R2b reference data. Covered by PaymentMethodIT."),
            java.util.Map.entry("PATCH /api/payment-methods/{method}/sort-code",
                    "R2b ordering only. Covered by PaymentMethodIT."),
            java.util.Map.entry("POST /api/payment-methods/{method}/deactivate",
                    "⚠️ R2b: deactivating a method mid-quarter would change what the narrative's own invoices can be settled by. Covered by PaymentMethodIT and R2ReferenceDataContractIT."),
            java.util.Map.entry("POST /api/payment-methods/{method}/reactivate",
                    "⚠️ R2b: see deactivate. Covered by PaymentMethodIT."),
            java.util.Map.entry("PUT /api/purchase-document-series/{id}/transformation-target",
                    "same as the sales series, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/purchase-document-types/{id}/aade-invoice-type",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/purchase-document-types/{id}/mydata-transmission",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/purchase-document-types/{id}/stock-behaviour",
                    "same as the sales document types, on the purchase side. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/sales-document-series/{id}/channel",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/sales-document-series/{id}/transformation-target",
                    "R1a reference data, shipped empty. ⚠️ Novocore records numbers and does not generate them, so a series is not something the quarter allocates against — R1b is where an invoice gains one. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/sales-document-types/{id}/aade-invoice-type",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/sales-document-types/{id}/mydata-transmission",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),
            java.util.Map.entry("PUT /api/sales-document-types/{id}/stock-behaviour",
                    "R1a reference data, and the table ships EMPTY on purpose: the owner authors his own through R2's screens. The quarter predates document types entirely — R1b is the step that makes one mandatory on a sales invoice. Covered by DocumentReferenceDataEndpointIT."),

            java.util.Map.entry("GET /api/email/outbox",
                    "no route sends email; a trading narrative cannot put a message in the outbox "
                            + "without calling EmailSender directly. Covered by OutboxEndpointIT."),
            java.util.Map.entry("GET /api/email/outbox/{id}",
                    "same — see GET /api/email/outbox. Covered by OutboxEndpointIT."),
            java.util.Map.entry("GET /api/email/outbox/{id}/attachments",
                    "same, and Q44's access path is asserted where the attachments exist: "
                            + "OutboxEndpointIT."),
            java.util.Map.entry("GET /api/email/attachments/{id}/content",
                    "same. Q44's other half — a referenced document re-checks its own record's "
                            + "section — is asserted by OutboxEndpointIT, which has a document to "
                            + "reference."),
            java.util.Map.entry("POST /api/email/outbox/{id}/retry",
                    "re-queueing needs a FAILED message, which needs a send that failed. "
                            + "OutboxEndpointIT arranges one; nothing an operator does over HTTP "
                            + "produces it."),
            java.util.Map.entry("GET /api/me",
                    "session identity, not a trading action — it says who is logged in and what "
                            + "they may reach, and nothing about the quarter. Covered by MeIT, "
                            + "which also asserts the thing that matters about it: a role granted "
                            + "nothing still reaches it."),
            java.util.Map.entry("PATCH /api/me/language",
                    "same — a display preference the backend stores and never reads (Q47b). "
                            + "Driving it here would change the owner's language mid-narrative to "
                            + "assert nothing. Covered by MeIT."),

            // Step 16b — user and role administration. All eighteen covered by UserRoleEndpointIT,
            // with the two that carry a security guarantee also asserted where that guarantee lives:
            // eviction in SessionEvictionIT, escalation in PrivilegeEscalationIT.
            java.util.Map.entry("GET /api/users",
                    "user administration, not trading. Covered by UserRoleEndpointIT."),
            java.util.Map.entry("GET /api/users/{id}",
                    "same — see GET /api/users."),
            java.util.Map.entry("POST /api/users",
                    "creating accounts mid-quarter would change who the narrative's own documents "
                            + "are attributed to. Covered by UserRoleEndpointIT, and its escalation "
                            + "refusal by PrivilegeEscalationIT."),
            java.util.Map.entry("PATCH /api/users/{id}/display-name",
                    "same — see POST /api/users."),
            java.util.Map.entry("PATCH /api/users/{id}/role",
                    "ends the target's sessions, so driving it here would log the narrative out of "
                            + "its own session. Covered by UserRoleEndpointIT and SessionEvictionIT."),
            java.util.Map.entry("PATCH /api/users/{id}/password",
                    "same — it ends sessions. Covered by UserRoleEndpointIT and SessionEvictionIT."),
            java.util.Map.entry("POST /api/users/{id}/deactivate",
                    "same, and the quarter has exactly one operator to deactivate. Covered by "
                            + "UserRoleEndpointIT and SessionEvictionIT."),
            java.util.Map.entry("POST /api/users/{id}/reactivate",
                    "same — see POST /api/users/{id}/deactivate."),
            java.util.Map.entry("GET /api/sections",
                    "the catalogue a role editor renders from; says nothing about a quarter. "
                            + "Covered by UserRoleEndpointIT."),
            java.util.Map.entry("GET /api/roles",
                    "role administration, not trading. Covered by UserRoleEndpointIT."),
            java.util.Map.entry("GET /api/roles/{id}",
                    "same — see GET /api/roles."),
            java.util.Map.entry("GET /api/roles/{id}/users",
                    "same, and it exists to answer a refusal the quarter never provokes. Covered "
                            + "by UserRoleEndpointIT."),
            java.util.Map.entry("POST /api/roles",
                    "same — see GET /api/roles."),
            java.util.Map.entry("PATCH /api/roles/{id}/name",
                    "same — see GET /api/roles."),
            java.util.Map.entry("PATCH /api/roles/{id}/description",
                    "same — see GET /api/roles. Added by backend queue item 5 on 2026-08-03 and "
                            + "driven end to end by UserRoleEndpointIT.roleLifecycle, including "
                            + "the clear-by-blank case and the system-role refusal."),
            java.util.Map.entry("PUT /api/roles/{id}/grants/{section}",
                    "narrowing a grant ends the sessions of everyone holding the role, including "
                            + "the narrative's own. Covered by UserRoleEndpointIT, its eviction by "
                            + "SessionEvictionIT and its escalation refusal by PrivilegeEscalationIT."),
            java.util.Map.entry("PUT /api/roles/{id}/field-restrictions/{field}",
                    "same — restricting a field ends sessions too. Covered by UserRoleEndpointIT "
                            + "and SessionEvictionIT."),
            java.util.Map.entry("POST /api/roles/{id}/deactivate",
                    "refused while anybody holds the role, which is every role in the quarter. "
                            + "Covered by UserRoleEndpointIT."),
            java.util.Map.entry("POST /api/roles/{id}/reactivate",
                    "same — see POST /api/roles/{id}/deactivate."),

            // Step 16b — Settings. Configuration, not trading, and changing any of it mid-narrative
            // would alter how the quarter's own remaining documents behave: the rounding threshold
            // decides which invoices need acceptance, and the cash limit decides which sales are
            // refused. A scenario that edits the rules it is being measured against proves nothing.
            java.util.Map.entry("GET /api/settings",
                    "configuration, not trading. Covered by SettingsEndpointIT."),
            java.util.Map.entry("GET /api/settings/{key}",
                    "same — see GET /api/settings."),
            java.util.Map.entry("PUT /api/settings/{key}",
                    "changing the rounding threshold or the retention policy mid-quarter would "
                            + "change how the narrative's own later documents behave. Covered by "
                            + "SettingsEndpointIT."),

            // Step 16b — administering the two runtime-editable lookups. The quarter reads both
            // heavily and creates neither: it uses the seeded VAT classes and units, which is what
            // a real quarter does.
            java.util.Map.entry("POST /api/vat-classes",
                    "creating a VAT class is a statutory event, not something a trading quarter "
                            + "does. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("PATCH /api/vat-classes/{id}/description",
                    "same — see POST /api/vat-classes."),
            java.util.Map.entry("PUT /api/vat-classes/{id}/reduced-counterpart",
                    "the island-reduced mappings are seeded (V5); remapping one mid-quarter would "
                            + "change what later lines resolve to. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("DELETE /api/vat-classes/{id}/reduced-counterpart",
                    "same — see PUT /api/vat-classes/{id}/reduced-counterpart."),
            java.util.Map.entry("POST /api/vat-classes/{id}/deactivate",
                    "deactivating a class the quarter's own invoices use would be arranging a "
                            + "failure rather than trading. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("POST /api/vat-classes/{id}/reactivate",
                    "same — see POST /api/vat-classes/{id}/deactivate."),
            java.util.Map.entry("POST /api/units-of-measure",
                    "the quarter uses the seeded units. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("PATCH /api/units-of-measure/{id}/name",
                    "same — see POST /api/units-of-measure."),
            java.util.Map.entry("PATCH /api/units-of-measure/{id}/mydata-code",
                    "the codes are pending the accountant (Q38), so there is no real value to "
                            + "record. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("PATCH /api/units-of-measure/{id}/fractional-quantity",
                    "flipping this mid-quarter would change whether the narrative's own quantities "
                            + "are valid. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("POST /api/units-of-measure/{id}/deactivate",
                    "refused while any product uses it, which is every seeded unit the quarter "
                            + "touches. Covered by LookupAdminEndpointIT."),
            java.util.Map.entry("POST /api/units-of-measure/{id}/reactivate",
                    "same — see POST /api/units-of-measure/{id}/deactivate."),
            java.util.Map.entry("GET /api/units-of-measure/without-mydata-code",
                    "the list still waiting on the accountant (Q38) — an outstanding question, not "
                            + "a trading action. Covered by LookupAdminEndpointIT."));

    // The three journal routes are deliberately NOT excused. The quarter posts hundreds of real
    // entries and runs as the Owner, so it is the only place with a realistic ledger to read — and
    // reading it here cross-checks the paged summary against the full entry, which is the one
    // assertion JournalEndpointIT's purpose-built fixtures cannot make as convincingly.

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
        quarter.happens();
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
     * The journal listing, read against a real quarter's postings.
     *
     * <p><strong>The cross-check is the point.</strong> A paged listing returns
     * {@code JournalEntrySummaryView}, whose {@code total} comes from a separate aggregate query —
     * not from the lines the single-entry route returns. Those two must agree, and nothing but a test
     * that reads both can say so. A purpose-built fixture would assert it against two entries; this
     * asserts it against a quarter of real trading, including reversals, credit notes, freight
     * allocations and write-offs.
     */
    @Test
    @Order(21)
    @DisplayName("the journal listing agrees with the entries it summarises")
    void journalListingAgreesWithTheEntries() {
        ApiClient.Session owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);

        JsonNode listing = Json.ok(
                owner.get("/api/journal-entries?size=50"), "GET /api/journal-entries");

        assertThat(listing.get("page").get("totalElements").asLong())
                .as("a quarter of trading posts a great many entries")
                .isGreaterThan(20L);

        // Every row on the page: fetch the full entry and compare. The summary's total is computed
        // by an aggregate over the lines; the entry's is computed from the lines themselves.
        int checked = 0;
        for (JsonNode summary : listing.get("items")) {
            long id = summary.get("id").asLong();
            JsonNode entry = Json.ok(
                    owner.get("/api/journal-entries/" + id), "GET /api/journal-entries/{id}");

            java.math.BigDecimal debits = java.math.BigDecimal.ZERO;
            int lines = 0;
            for (JsonNode line : entry.get("lines")) {
                lines++;
                if ("DEBIT".equals(Json.text(line, "side"))) {
                    debits = debits.add(new java.math.BigDecimal(Json.amount(line, "amount")));
                }
            }

            assertThat(new java.math.BigDecimal(Json.amount(summary, "total")))
                    .as("entry %d: the listing's total must equal the entry's own debits", id)
                    .isEqualByComparingTo(debits);
            assertThat(summary.get("lineCount").asInt())
                    .as("entry %d: the listing's line count", id)
                    .isEqualTo(lines);
            checked++;
        }
        assertThat(checked).isGreaterThan(20);

        // And the account ledger, on an account this quarter certainly posted to.
        long inventoryId = Json.ok(owner.get("/api/accounts"), "GET /api/accounts")
                .get("items").valueStream()
                .filter(account -> "INVENTORY".equals(Json.text(account, "systemKey")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No account carries the INVENTORY system key."))
                .get("id").asLong();

        JsonNode ledger = Json.ok(
                owner.get("/api/accounts/" + inventoryId + "/ledger?size=10"),
                "GET /api/accounts/{id}/ledger");
        assertThat(ledger.get("items")).isNotEmpty();
        assertThat(ledger.get("page").get("totalElements").asLong()).isPositive();
        for (JsonNode line : ledger.get("items")) {
            assertThat(line.get("accountId").asLong())
                    .as("a ledger must contain only the account it is a ledger of")
                    .isEqualTo(inventoryId);
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
