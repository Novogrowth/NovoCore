package gr.novotrade.novocore.core.api.tax;

import java.util.Objects;

/**
 * The VAT class that applies to a line, and which level it came from.
 *
 * @param vatClassId the winning VAT class
 * @param source the level that supplied it, for explaining the result afterwards
 */
public record VatClassResolution(long vatClassId, VatClassSource source) {

    public VatClassResolution {
        Objects.requireNonNull(source, "source");
    }

    /** True when the rate came from an override rather than the product's own default. */
    public boolean isOverride() {
        return source != VatClassSource.PRODUCT;
    }
}
