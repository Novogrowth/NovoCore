package gr.novotrade.novocore.core.api.tax;

import java.util.Optional;

/**
 * The rule deciding which VAT class applies to a sale line when more than one level specifies
 * one.
 *
 * <p><strong>Invoice line beats customer, customer beats product.</strong> Stated here, in one
 * place, as executable code — not left to be inferred from the order of a chain of null checks
 * written independently at each call site. Three call sites implementing "the obvious"
 * precedence is how two of them end up disagreeing, and a VAT rate chosen by the wrong level is
 * a real invoice defect rather than a cosmetic one.
 *
 * <p><strong>There is deliberately no fallback rate.</strong> If no level specifies a VAT class,
 * {@link #resolve} throws instead of assuming the standard 24%. A silent default is the worst
 * available outcome here: it produces a plausible invoice at a rate nobody chose, and an
 * undercharge is not recoverable from the customer afterwards. This is {@code CLAUDE.md} rule 7
 * — auto-resolve only what is genuinely certain, and never guess.
 *
 * <p>Takes ids rather than {@link VatClassView} objects on purpose. The rule is about precedence,
 * not about VAT classes, so it can be applied before Product, Customer and Sales Invoice exist
 * (steps 5 and 9) and without loading three rows to pick one of them.
 */
public final class VatClassPrecedence {

    private VatClassPrecedence() {
    }

    /**
     * Applies the precedence rule.
     *
     * @param invoiceLineOverride the VAT class set on the line itself, or null
     * @param customerOverride the VAT class set on the customer, or null
     * @param productDefault the product's default VAT class, or null
     * @throws VatClassNotDeterminableException if all three are absent
     */
    public static VatClassResolution resolve(
            Long invoiceLineOverride, Long customerOverride, Long productDefault) {
        return find(invoiceLineOverride, customerOverride, productDefault)
                .orElseThrow(VatClassNotDeterminableException::new);
    }

    /**
     * As {@link #resolve}, but empty rather than throwing when no level specifies a class.
     *
     * <p>For callers that genuinely have somewhere to put the question — a draft invoice that can
     * show the line as needing attention, say. A caller that would respond to the empty case by
     * picking a rate itself should use {@link #resolve} instead.
     */
    public static Optional<VatClassResolution> find(
            Long invoiceLineOverride, Long customerOverride, Long productDefault) {
        if (invoiceLineOverride != null) {
            return Optional.of(
                    new VatClassResolution(invoiceLineOverride, VatClassSource.INVOICE_LINE));
        }
        if (customerOverride != null) {
            return Optional.of(new VatClassResolution(customerOverride, VatClassSource.CUSTOMER));
        }
        if (productDefault != null) {
            return Optional.of(new VatClassResolution(productDefault, VatClassSource.PRODUCT));
        }
        return Optional.empty();
    }
}
