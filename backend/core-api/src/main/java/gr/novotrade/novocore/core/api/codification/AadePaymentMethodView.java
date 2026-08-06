package gr.novotrade.novocore.core.api.codification;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * One payment-method article from <strong>annex 8.12</strong> of the myDATA v2.0.1 ERP specification.
 *
 * <p>A {@link StatutoryCodification}: AADE authors these rows, Flyway writes them, and there is no
 * create path on any installation, ever.
 *
 * <h2>⚠️ Its codes are the one annex the XSDs do not carry</h2>
 *
 * <p>{@code paymentMethods-v2.0.1.xsd} defines no code list at all. The type lives in
 * {@code InvoicesDoc-v2.0.1.xsd} and is a <strong>range</strong> — {@code xs:int}, {@code 1}–{@code 8}
 * — which says how many codes exist and nothing about what any of them means. So {@code CLAUDE.md}'s
 * standing rule, <em>codes from the XSD and descriptions from the annex</em>, <strong>has no safe side
 * here</strong>: both halves can only come from annex 8.12. They were read from a rasterised page on
 * 2026-08-06 and agree with an independent rasterised read made in R1a. See {@code V37} and
 * {@code docs/aade/v2.0.1/README.md} §5.
 *
 * <h2>⚠️ No derived accessors, deliberately</h2>
 *
 * <p>{@code AadeInvoiceTypeView} carried one that <em>threw</em> for six codes, and
 * {@code GET /api/aade-invoice-types} answered {@code 500} for the whole codification. This record has
 * none, and {@code AadeInvoiceTypeIT.theViewHasNoDerivedAccessorThatCanThrow} exists to keep it that
 * way one table over.
 *
 * @param code AADE's own code, {@code 1}–{@code 8}. An {@code int} rather than text, unlike an invoice
 *     type's {@code 13.30}, because these genuinely are integers and text would invite {@code "03"}.
 * @param active ⚠️ Means <strong>"AADE still publishes this code"</strong>, never "this business uses
 *     it". The second question belongs to {@code PaymentMethodView.active}, one table over.
 */
public record AadePaymentMethodView(
        long id,
        int code,
        @Mandatory String description,
        boolean active) {

    public AadePaymentMethodView {
        Objects.requireNonNull(description, "description");
    }
}
