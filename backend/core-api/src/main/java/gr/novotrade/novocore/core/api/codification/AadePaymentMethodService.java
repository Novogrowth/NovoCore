package gr.novotrade.novocore.core.api.codification;

/**
 * Annex 8.12's payment-method articles — <strong>read, describe, deactivate, reactivate.</strong>
 *
 * <p>The whole content of this interface is the method that is absent: there is no {@code create},
 * because a row here is AADE's to define and not ours.
 * {@code StatutoryCodificationRulesTest} makes that a build failure rather than a convention.
 *
 * <p>⚠️ <strong>This is the codification, not the business's list.</strong> The business's payment
 * methods live behind {@code PaymentMethodService}, are user-created, and each one references exactly
 * one row here. Conflating the two is the mistake R4 exists to correct, one entity over from R1a's.
 */
public interface AadePaymentMethodService extends StatutoryCodification<AadePaymentMethodView> {
}
