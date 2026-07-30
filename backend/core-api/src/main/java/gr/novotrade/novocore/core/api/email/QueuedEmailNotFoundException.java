package gr.novotrade.novocore.core.api.email;

/**
 * No such queued email.
 *
 * <p>Added by step 15, and it is the third instance of one pattern rather than a gap in step 11's
 * work. {@code GET /api/email/outbox/{id}} signalled an unknown id with an
 * {@code IllegalArgumentException}, which step 14 settled means a <em>programming</em> error — so
 * {@code WebExceptionHandler} logged the message and answered a bare {@code 400 "Bad request."} while
 * every other "this id names nothing" route on the surface answers {@code 404 "Not found."}. Two
 * things were wrong with that and only one of them is the discarded message: a client cannot tell a
 * malformed request from a missing record, and this was the single route on which it could not.
 *
 * <p>Its message is deliberately never returned — the 404 handler answers generically, because
 * echoing "no queued email with id 41" back confirms the existence of neighbouring records to a
 * caller probing ids. The message is for the log, which is where it belongs.
 */
public class QueuedEmailNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueuedEmailNotFoundException(long id) {
        super("No queued email with id " + id + ".");
    }
}
