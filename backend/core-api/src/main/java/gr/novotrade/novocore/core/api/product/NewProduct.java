package gr.novotrade.novocore.core.api.product;

import gr.novotrade.novocore.core.api.shared.Money;
import java.util.Objects;

/**
 * Request to add a product.
 *
 * @param defaultVatClassId required. There is no fallback VAT rate anywhere in NovoCore — see
 *     {@code VatClassPrecedence} — so a product without one could be created and then not
 *     invoiced, which moves the failure from creation, where it is obvious and cheap, to the
 *     middle of a sale.
 * @param sellingPrice may be null. A product imported from an external catalogue or created
 *     barcode-first may genuinely not have its price yet, and refusing to record the product at
 *     all would be worse than recording it unpriced; the check belongs at the point of sale
 *     instead.
 * @param supplierId one supplier or none (Q5). A supplier SKU without a supplier is refused.
 */
public record NewProduct(
        String sku,
        String ean,
        String name,
        ProductType type,
        UnitOfMeasure unitOfMeasure,
        long defaultVatClassId,
        Money sellingPrice,
        Long supplierId,
        String supplierSku) {

    public NewProduct {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    }

    /** A stocked item sold by the piece, with no supplier recorded yet. */
    public static NewProduct goods(
            String sku, String name, long defaultVatClassId, Money sellingPrice) {
        return new NewProduct(sku, null, name, ProductType.GOODS, UnitOfMeasure.PIECE,
                defaultVatClassId, sellingPrice, null, null);
    }

    /** A service: no stock, no supplier, no barcode. */
    public static NewProduct service(
            String sku, String name, long defaultVatClassId, Money sellingPrice) {
        return new NewProduct(sku, null, name, ProductType.SERVICE, UnitOfMeasure.PIECE,
                defaultVatClassId, sellingPrice, null, null);
    }
}
