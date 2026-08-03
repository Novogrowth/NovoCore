package gr.novotrade.novocore.core.api.document;

/**
 * A document series the domain refuses — a duplicate abbreviation, a transformation target that is
 * the series itself, or a series pointing at an inactive document type.
 */
public class InvalidDocumentSeriesException extends RuntimeException {

    public InvalidDocumentSeriesException(String message) {
        super(message);
    }
}
