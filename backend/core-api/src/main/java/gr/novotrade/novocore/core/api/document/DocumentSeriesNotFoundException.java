package gr.novotrade.novocore.core.api.document;

/** No sales or purchase document series with that id. */
public class DocumentSeriesNotFoundException extends RuntimeException {

    private DocumentSeriesNotFoundException(String message) {
        super(message);
    }

    public static DocumentSeriesNotFoundException sales(long id) {
        return new DocumentSeriesNotFoundException("No sales document series with id " + id + ".");
    }

    public static DocumentSeriesNotFoundException purchase(long id) {
        return new DocumentSeriesNotFoundException(
                "No purchase document series with id " + id + ".");
    }
}
