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
 * @param serialTracked whether stock of this product is identified individually by serial number
 *     (brief §5). Stated at creation rather than set afterwards because it is knowable then and
 *     because it cannot be changed once stock has arrived: a lot is received in one shape or the
 *     other, and reinterpreting a pooled quantity of five as five anonymous serial numbers is not a
 *     correction, it is an invention. {@code ProductService.changeSerialTracking} exists for the
 *     window before any lot exists.
 *     <p>Only a stocked product can be one — a service has no units to number.
 *     <p>There is deliberately no bundle flag here: a bundle needs its components, and
 *     {@code BundleService.define} sets both in one transaction so a bundle never exists empty.
 */
public record NewProduct(
        String sku,
        String ean,
        String name,
        ProductType type,
        long unitOfMeasureId,
        long defaultVatClassId,
        Money sellingPrice,
        Long supplierId,
        String supplierSku,
        boolean serialTracked) {

    public NewProduct {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    /** A stocked item held as pooled stock, with no barcode and no supplier recorded yet. */
    public static NewProduct goods(String sku, String name, long unitOfMeasureId,
            long defaultVatClassId, Money sellingPrice) {
        return new NewProduct(sku, null, name, ProductType.GOODS, unitOfMeasureId,
                defaultVatClassId, sellingPrice, null, null, false);
    }

    /**
     * A stocked item whose units are individually identified by serial number — brief §5's example
     * being a coffee machine.
     */
    public static NewProduct serializedGoods(String sku, String name, long unitOfMeasureId,
            long defaultVatClassId, Money sellingPrice) {
        return new NewProduct(sku, null, name, ProductType.GOODS, unitOfMeasureId,
                defaultVatClassId, sellingPrice, null, null, true);
    }

    /** A service: no stock, no supplier, no barcode. */
    public static NewProduct service(String sku, String name, long unitOfMeasureId,
            long defaultVatClassId, Money sellingPrice) {
        return new NewProduct(sku, null, name, ProductType.SERVICE, unitOfMeasureId,
                defaultVatClassId, sellingPrice, null, null, false);
    }
}
