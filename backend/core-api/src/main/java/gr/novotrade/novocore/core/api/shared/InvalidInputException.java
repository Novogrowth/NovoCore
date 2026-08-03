package gr.novotrade.novocore.core.api.shared;

/**
 * <strong>The caller supplied a structurally incomplete or unanswerable command, and the message
 * says how to supply a proper one.</strong>
 *
 * <p>Two shapes, and they are the same mistake: a required field left out of a request record's
 * compact constructor (see {@link Required}), and a combination of listing parameters a route
 * cannot answer — a {@code productId}, or a {@code from}/{@code to} pair, or {@code partyType}
 * together with {@code partyId}. Both are mistakes in what the <em>caller</em> supplied, not bugs in
 * calling code, and that difference decides whether the reason reaches the caller.
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
 * <h2>⚠️ Why it lives in {@code core-api}, and why it did not until Q1</h2>
 *
 * <p><strong>It sat in {@code core.web} until 2026-08-03, and that placement was the structural
 * cause of a defect rather than an opinion about layering.</strong> {@code core-api} has no
 * production dependencies at all — deliberately — so nothing in it could reach this class or
 * {@link Required}. {@code NewUser} and {@code NewRole} are request records that live here, and
 * they guarded their fields with {@code Objects.requireNonNull}: the fifth confirmed instance of
 * {@code CLAUDE.md}'s <em>a client's mistake raised as a programming error</em>. That was not a
 * lapse of attention. <strong>It was the only thing their author could have written</strong>,
 * because the prescribed remedy was not merely unused here — it was unreachable.
 *
 * <p>The earlier javadoc argued the opposite: that a parameter combination is <em>the HTTP surface's
 * own concern</em>, since the services below take typed arguments and have no notion of a query
 * string. That argument holds for the <em>query-parameter</em> shape and does not generalise. An
 * adapter calling {@code UserService.create} with a null username has made exactly the mistake an
 * HTTP client makes by omitting the key. <strong>Only the word "request" and the {@code 400}
 * mapping were ever web-shaped</strong>, so the name lost "Request" when the class moved and the
 * mapping stayed behind in {@code WebExceptionHandler}, where the argument for 400-over-422 is
 * written out.
 *
 * <p>This carries the same principle step 14 already applied to refusals: a permission refusal stays
 * generic because its detail would describe the permission model, while a validation refusal carries
 * its message because somebody who cannot see what was wrong cannot put it right.
 */
public class InvalidInputException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}
