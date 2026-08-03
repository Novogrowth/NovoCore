package gr.novotrade.novocore.core.api.document;

/** No sales or purchase document type with that id. */
public class DocumentTypeNotFoundException extends RuntimeException {

    private DocumentTypeNotFoundException(String message) {
        super(message);
    }

    public static DocumentTypeNotFoundException sales(long id) {
        return new DocumentTypeNotFoundException("No sales document type with id " + id + ".");
    }

    public static DocumentTypeNotFoundException purchase(long id) {
        return new DocumentTypeNotFoundException("No purchase document type with id " + id + ".");
    }
}
