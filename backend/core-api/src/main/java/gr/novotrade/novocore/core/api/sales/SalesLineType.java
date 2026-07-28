package gr.novotrade.novocore.core.api.sales;

/**
 * The two shapes a sales invoice line comes in — V12's rule for lots and step 8's rule for purchase
 * invoice lines, applied to the sales side.
 *
 * <p>Not a categorisation scheme. It is the difference between a line that sells something out of the
 * catalogue and a line that charges a fee, and it decides which account is credited and whether any
 * stock leaves.
 */
public enum SalesLineType {

    /**
     * Something out of the catalogue: goods, a service, or a bundle.
     *
     * <p>The product decides the rest — a {@code SERVICE} credits {@code Services}, goods credit the
     * channel's Sales account, a bundle credits at bundle level and consumes at component level.
     */
    PRODUCT,

    /**
     * A fee charged to the customer as revenue, through the {@code ChargeType} lookup V7 seeded:
     * delivery, COD fee.
     *
     * <p>The income account is the charge type's, which is an operator decision per fee rather than a
     * rule compiled into the software — and {@code ChargeTypeService} refuses a non-{@code INCOME}
     * account, which is why netting a delivery fee off {@code Transportation costs} is impossible
     * rather than merely discouraged.
     *
     * <p><strong>Q33, settled and confirmed with the accountant: a fee's VAT rate is independent of
     * the products on the invoice.</strong> A 13% order still carries 24% delivery. Nothing derives a
     * charge line's rate from the lines around it, and nothing should later be built to.
     */
    CHARGE
}
