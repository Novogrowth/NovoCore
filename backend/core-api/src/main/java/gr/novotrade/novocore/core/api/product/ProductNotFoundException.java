package gr.novotrade.novocore.core.api.product;

/** No such product. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long id) {
        super("No product with id " + id + ".");
    }

    private ProductNotFoundException(String message) {
        super(message);
    }

    public static ProductNotFoundException forSku(String sku) {
        return new ProductNotFoundException("No product with SKU '" + sku + "'.");
    }
}
