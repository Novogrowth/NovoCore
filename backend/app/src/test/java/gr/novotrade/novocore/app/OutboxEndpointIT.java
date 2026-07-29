package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentOwnerType;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The email outbox over HTTP, and Q44 as a caller actually experiences it.
 *
 * <p>{@code EmailAttachmentAccessIT} proves the check inside the service. This proves the same rule
 * survives the whole stack — the filter chain, the {@code @Requires} interceptor, the controller and
 * the service together — because a guarantee that holds in a unit test and not over HTTP is not a
 * guarantee about the system.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + OutboxEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + OutboxEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class OutboxEndpointIT {

    static final String OWNER_USERNAME = "outbox.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String CLERK_USERNAME = "outbox.clerk";
    private static final String CLERK_PASSWORD = "clerk-password-long-enough";

    private static final String OUTSIDER_USERNAME = "outbox.outsider";
    private static final String OUTSIDER_PASSWORD = "outsider-password-long-enough";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserService users;

    @Autowired
    private RoleService roles;

    @Autowired
    private EmailSender email;

    @Autowired
    private AttachmentService attachments;

    private ApiClient api;
    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    @Test
    @DisplayName("the outbox is readable, and bodies are not in it")
    void theOutboxIsReadable() {
        email.send(EmailMessage.to("someone@example.com", "OutboxIT subject",
                "SECRET-BODY-TEXT-that-must-not-appear"));

        ResponseEntity<String> response = owner.get("/api/email/outbox?status=PENDING");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("OutboxIT subject");
        // Bodies are absent from QueuedEmailView by design — an outbox screen is a delivery log,
        // not a copy of every customer email.
        assertThat(response.getBody()).doesNotContain("SECRET-BODY-TEXT");
    }

    @Test
    @DisplayName("a role without EMAIL_OUTBOX cannot see it at all")
    void theSectionIsEnforced() {
        // Deliberately a role that has a section and not this one: it proves the grant is
        // per-section rather than "authenticated is enough", which a role holding nothing at all
        // could not distinguish.
        ApiClient.Session outsider = outsiderSession();

        assertThat(outsider.get("/api/email/outbox?status=PENDING").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(outsider.get("/api/customers").getStatusCode())
                .as("the outsider really does hold another section, so this is not a blanket refusal")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Q44 over HTTP: the outbox is not a second way into a purchase invoice's PDF")
    void q44OverHttp() {
        AttachmentMetadata document = attachments.attach(
                AttachmentOwnerType.PURCHASE_INVOICE.entityType(), "8001",
                "supplier-invoice.pdf", "application/pdf",
                "%PDF-1.7 SUPPLIER-PRICES".getBytes(StandardCharsets.UTF_8));
        long emailId = email.send(EmailMessage.to(
                "accountant@example.com", "OutboxIT invoice", "Attached.",
                EmailAttachment.stored(document.id())));
        long attachmentId = email.attachmentsOf(emailId).getFirst().id();

        ApiClient.Session clerk = clerkSession();

        // The clerk holds EMAIL_OUTBOX and not PURCHASING. They can see the message and the
        // attachment's name, size and type...
        assertThat(clerk.get("/api/email/outbox/" + emailId + "/attachments").getBody())
                .contains("supplier-invoice.pdf");

        // ...and cannot have the bytes, because the document belongs to a purchase invoice.
        ResponseEntity<String> denied =
                clerk.get("/api/email/attachments/" + attachmentId + "/content");

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).doesNotContain("SUPPLIER-PRICES");

        // The Owner, who may open the purchase invoice, gets the file.
        ResponseEntity<String> allowed =
                owner.get("/api/email/attachments/" + attachmentId + "/content");

        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getBody()).contains("SUPPLIER-PRICES");
        assertThat(allowed.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment")
                .contains("supplier-invoice.pdf");
    }

    @Test
    @DisplayName("an inline attachment needs only the outbox section")
    void inlineNeedsOnlyTheOutbox() {
        long emailId = email.send(EmailMessage.to(
                "owner@example.com", "OutboxIT report", "Attached.",
                EmailAttachment.pdf("report.pdf",
                        "%PDF-1.7 GENERATED-REPORT".getBytes(StandardCharsets.UTF_8))));
        long attachmentId = email.attachmentsOf(emailId).getFirst().id();

        ApiClient.Session clerk = clerkSession();

        // Nothing to re-check against: these bytes exist nowhere else.
        ResponseEntity<String> response =
                clerk.get("/api/email/attachments/" + attachmentId + "/content");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("GENERATED-REPORT");
    }

    @Test
    @DisplayName("retry needs FULL, not VIEW")
    void retryNeedsFullAccess() {
        long emailId = email.send(EmailMessage.to("someone@example.com", "OutboxIT retry", "Body"));
        ApiClient.Session clerk = clerkSession();

        // The clerk's grant is VIEW. Reading the outbox is not permission to act on it.
        assertThat(clerk.post("/api/email/outbox/" + emailId + "/retry", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an unsupported status filter is refused rather than silently reinterpreted")
    void anUnsupportedFilterIsRefused() {
        assertThat(owner.get("/api/email/outbox?status=SENT").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------------------------

    /** EMAIL_OUTBOX at VIEW, and nothing else — the role Q44 is about. */
    private ApiClient.Session clerkSession() {
        if (users.findByUsername(CLERK_USERNAME).isEmpty()) {
            RoleView role = roles.create(new NewRole("OUTBOX_CLERK", "Reads the email outbox"));
            roles.grant(role.id(), Section.EMAIL_OUTBOX, AccessLevel.VIEW);
            users.create(new NewUser(CLERK_USERNAME, "Outbox Clerk", CLERK_PASSWORD, role.id()));
        }
        return api.logIn(CLERK_USERNAME, CLERK_PASSWORD);
    }

    /** CUSTOMERS and nothing else — has a section, does not have this one. */
    private ApiClient.Session outsiderSession() {
        if (users.findByUsername(OUTSIDER_USERNAME).isEmpty()) {
            RoleView role = roles.create(new NewRole("OUTBOX_OUTSIDER", "No outbox access"));
            roles.grant(role.id(), Section.CUSTOMERS, AccessLevel.VIEW);
            users.create(new NewUser(
                    OUTSIDER_USERNAME, "Outbox Outsider", OUTSIDER_PASSWORD, role.id()));
        }
        return api.logIn(OUTSIDER_USERNAME, OUTSIDER_PASSWORD);
    }

}
