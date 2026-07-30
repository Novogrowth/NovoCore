package gr.novotrade.novocore.core.api.email;

/**
 * No such email attachment.
 *
 * <p><strong>Deliberately distinct from {@link EmailAttachmentUnavailableException}</strong>, and ADR
 * 0012 turns on that distinction: an attachment whose bytes have been deleted or pruned still exists
 * as a record, still names the file, and answers {@code 410} <em>with the reason</em>, because the
 * message really did go out with that file on it. This is the other case — an id that never named
 * anything — and it is a {@code 404}.
 *
 * <p>It replaces an {@code IllegalArgumentException}, which is what step 15 found the whole email
 * slice using for ids that name nothing. That is the exception the web layer treats as a bug in our
 * own code, so the caller received {@code 400 "Bad request."} — no reason, and the wrong status.
 * Found by {@code PermissionSweepIT.noRouteRefusesWithoutSayingWhy}, which is the half of the guard
 * that can see a service-layer throw; the ArchUnit rule beside it only sees the web layer.
 */
public class EmailAttachmentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAttachmentNotFoundException(long id) {
        super("No email attachment with id " + id + ".");
    }
}
