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
 *
 * <h2>⚠️ THE ISLAND REDUCED RATE IS NOT IN THIS CHAIN, AND IS NOT IMPLEMENTED ANYWHERE</h2>
 *
 * <p><strong>Established by measurement in F5, 2026-08-05 (B.6). Stated here because this is the
 * file a reader consults to learn how a line's rate is decided, and the honest answer includes what
 * is missing from it.</strong>
 *
 * <p>{@code VatClass.reducedCounterpart} exists and is <strong>seeded</strong> — {@code V5} carries
 * the real chains (24→17, 13→9, 6→4→1041, the last being the αρ.31 ν.5057/2023 variant) — and it has
 * administration routes. <strong>It is read by nothing outside {@code ..core.tax..}.</strong> No
 * pricing path consults it: {@code SalesInvoiceServiceImpl.price} resolves through this class and
 * then takes {@code vatClass.ratePercent()} directly. <em>It is a lookup table waiting for a rule.</em>
 *
 * <p>⚠️ <strong>And nothing could feed such a rule today even if it existed.</strong> Deciding that a
 * supply is island-reduced needs to know where the goods are going, and {@code Customer} has no
 * address, no postcode and no region — structured addresses are <strong>D3</strong>, which is
 * scheduled <em>after</em> F5. So the gap is two things, not one: no rule, and no input.
 *
 * <p><strong>Why this matters to whoever answers it.</strong> The precedence between a product's VAT
 * class, the island mapping and a customer override is an <strong>open question with the owner's
 * external accountant</strong>, and applicability is already decided — Java Jives ships to
 * reduced-VAT islands. When the answer arrives it may not merely <em>reorder</em> line → customer →
 * product: an island rate is a fact about a <em>destination</em>, so it plausibly enters as a fourth
 * input that none of these three carries. ⚠️ D3 already records the same shape for addresses —
 * <em>enforced at the DOCUMENT, not the customer</em> — and a destination-driven rate would be
 * per-document for exactly that reason.
 *
 * <p><strong>F5's sales-invoice line form is built directly on top of this method and is therefore
 * downstream of that answer.</strong> Its line has a VAT-class override and an exemption reason and
 * nothing else, because those are the only levels that exist. Whoever implements the island rule
 * should expect to reopen that form.
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
