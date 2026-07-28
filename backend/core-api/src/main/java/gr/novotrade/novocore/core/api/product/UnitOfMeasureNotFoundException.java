package gr.novotrade.novocore.core.api.product;

/** No such unit of measure. */
public class UnitOfMeasureNotFoundException extends RuntimeException {

    public UnitOfMeasureNotFoundException(long id) {
        super("No unit of measure with id " + id + ".");
    }

    private UnitOfMeasureNotFoundException(String message) {
        super(message);
    }

    public static UnitOfMeasureNotFoundException forCode(String code) {
        return new UnitOfMeasureNotFoundException("No unit of measure with code '" + code + "'.");
    }
}
