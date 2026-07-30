package gr.novotrade.novocore.core.web;

/**
 * <strong>A field of a request body that the route cannot work without.</strong>
 *
 * <p>Found by step 15's permission sweep, which asked every state-changing route in the surface for a
 * request with an <em>empty body</em> — the commonest thing a real client does wrong, and something
 * nothing had ever done. <strong>Sixteen of them answered 500.</strong> The cause was the same
 * everywhere: a missing field arrived as {@code null}, travelled into the core, and met an
 * {@code Objects.requireNonNull} written to catch a <em>programming</em> error. The core is right to
 * have those — it is a library to everything above it — but on this path the "caller" is a browser,
 * and the browser got a stack trace's worth of nothing.
 *
 * <p><strong>It is the same defect as step 15's defect 5, one exception class along.</strong> There,
 * controllers signalled a client mistake with {@code IllegalArgumentException}, which
 * {@code WebExceptionHandler} correctly treats as a bug and answers with a bare {@code "Bad
 * request."}; seventeen carefully written messages were discarded. Here a client mistake arrives as a
 * {@code NullPointerException}, which is not mapped at all — so it becomes a 500 and, worse, the one
 * response on this surface that is <em>not</em> RFC 7807: Boot's error page answers with the legacy
 * {@code {timestamp,status,error,path}} shape, with no {@code detail} for a client to read.
 *
 * <h2>Stated on the record, not at the call site</h2>
 *
 * <p>The check belongs in the request record's compact constructor rather than in each handler
 * method, and that is the whole design:
 *
 * <ul>
 *   <li>{@link ReversalCommand} is one record shared by <strong>six</strong> reversal routes. One
 *       statement fixes all six, and a seventh reversible document gets it for free — where six
 *       copies of a null check is exactly the kind of thing that ends up with five.
 *   <li>A record that refuses to exist in an invalid state cannot be forgotten by whoever writes the
 *       next handler that binds it.
 *   <li>The requirement reads next to the field it is about, which is where somebody looks.
 * </ul>
 *
 * <p>Jackson invokes the canonical constructor, so a throw here surfaces as an
 * {@code HttpMessageNotReadableException}. {@code WebExceptionHandler} unwraps that and reports an
 * {@link InvalidRequestException} cause as its own message — so this produces a plain 400 naming the
 * field, in the same body shape as every other refusal, rather than a message about malformed JSON
 * for a body that parsed perfectly well.
 *
 * <p><strong>400 and not 422</strong>, deliberately and for the same reason
 * {@link InvalidRequestException} is a 400: the caller can fix this by correcting what it sent. A 422
 * on this surface means the request was understood and a business rule refused it, which is a
 * different thing to tell a client.
 *
 * <p><strong>What keeps it from happening again</strong> is not this class but the test that found
 * it: {@code PermissionSweepIT.noRouteFailsOnAnEmptyBody} asks every state-changing route the same
 * question on every build, so a new route that fails instead of refusing fails the build naming
 * itself.
 */
public final class Required {

    private Required() {
    }

    /**
     * @param value the field's value as it arrived
     * @param name  the field's name <em>as the client wrote it</em> — this goes back over the wire,
     *              so it must match the JSON property and not the Java accessor if they differ
     * @return {@code value}, so the check reads as an assignment in a compact constructor
     * @throws InvalidRequestException if the value is absent
     */
    public static <T> T field(T value, String name) {
        if (value == null) {
            throw new InvalidRequestException(
                    "\"" + name + "\" is required and was not supplied.");
        }
        return value;
    }

    /**
     * A required field that must also say something.
     *
     * <p>Separate from {@link #field} because a blank string is a different mistake from a missing
     * one and the caller is better told which. Used where an empty value would otherwise reach the
     * core and be refused there with a message about the domain rather than about the request.
     */
    public static String text(String value, String name) {
        String supplied = field(value, name);
        if (supplied.isBlank()) {
            throw new InvalidRequestException("\"" + name + "\" is required and was blank.");
        }
        return supplied;
    }
}
