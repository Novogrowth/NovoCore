package gr.novotrade.novocore.core.api.document;

/**
 * A document type the domain refuses — a duplicate description, an AADE code from the wrong side of
 * annex 8.1, or an attempt to activate a type whose stock behaviour is still undecided.
 *
 * <p>A caller-facing refusal with its reason, deliberately not an {@code IllegalArgumentException}:
 * that type means <em>our</em> code is wrong and its message is correctly withheld, which would
 * leave an operator with a bare "Bad request." and no way to see why the type was rejected.
 */
public class InvalidDocumentTypeException extends RuntimeException {

    public InvalidDocumentTypeException(String message) {
        super(message);
    }
}
