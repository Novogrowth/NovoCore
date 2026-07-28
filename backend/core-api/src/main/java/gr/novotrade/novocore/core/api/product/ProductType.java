package gr.novotrade.novocore.core.api.product;

/**
 * Whether a product is a physical good or a service.
 *
 * <p>Not a cosmetic label. The distinction decides three things the core genuinely does
 * differently: a service has no inventory lots and therefore no FIFO cost (step 6), it credits
 * {@code Services} rather than a channel {@code Sales} account, and its cost side is
 * {@code Cost of service sold} rather than {@code Cost of goods sold} — two accounts that exist in
 * the seeded chart precisely because the two are not the same thing.
 *
 * <p>An enum rather than a lookup table, unlike VAT classes: which of these exist is determined by
 * what NovoCore has been built to handle, not by a statute or an external authority's list.
 */
public enum ProductType {

    /** A physical item. Carries stock, consumed FIFO from lots. */
    GOODS,

    /**
     * Labour or a service — a repair, a calibration, a training session.
     *
     * <p>Holds no stock. Anything asking a service product for its stock level is asking the wrong
     * question, and step 6 must refuse rather than answer zero: zero and "not applicable" look the
     * same on a screen and lead to a back-in-stock reminder for a service.
     */
    SERVICE;

    /** True when this type has inventory lots behind it. */
    public boolean isStocked() {
        return this == GOODS;
    }
}
