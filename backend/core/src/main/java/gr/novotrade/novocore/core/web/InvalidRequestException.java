package gr.novotrade.novocore.core.web;

/**
 * <strong>The caller asked for something this route cannot answer, and the message says how to ask
 * properly.</strong>
 *
 * <p>Several listing routes need one of a few parameter combinations — a {@code productId}, or a
 * {@code from}/{@code to} pair, or {@code partyType} together with {@code partyId} — and cannot
 * answer without one. That is a mistake in the <em>request</em>, not a bug in calling code, and the
 * difference decides whether the reason reaches the caller.
 *
 * <p><strong>Why this exists rather than an {@code IllegalArgumentException}.</strong> Step 14
 * settled that an {@code IllegalArgumentException} escaping the core is a programming error, so
 * {@code WebExceptionHandler} logs its message and returns a bare {@code 400} — correct, because
 * such a message describes internal state and is no use to whoever made the request. The
 * controllers then used that same exception for parameter guidance, and seventeen carefully written
 * messages across nine controllers — "name a productId or a location", "partyType and partyId go
 * together; name both" — were thrown away by a handler doing exactly what it was designed to do.
 *
 * <p>Step 15's narrative was the first thing to call those listings the way a client would and got
 * {@code "Bad request."} with no indication of what was wrong. A route whose only correct usage
 * cannot be discovered from its own error is a route a frontend is written against by guesswork.
 *
 * <p>This carries the same principle step 14 already applied to refusals: a permission refusal stays
 * generic because its detail would describe the permission model, while a validation refusal carries
 * its message because somebody who cannot see what was wrong cannot put it right.
 *
 * <p>It lives in the web layer, not {@code core-api}, because a parameter combination is the HTTP
 * surface's own concern — the services below take typed arguments and have no notion of a query
 * string.
 */
public class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }
}
