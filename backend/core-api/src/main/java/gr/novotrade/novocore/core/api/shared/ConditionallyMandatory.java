package gr.novotrade.novocore.core.api.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <strong>This component's compact constructor refuses a null only in some shape of the record, so it
 * is deliberately NOT {@link Mandatory}.</strong> The reason is required, and it is required
 * <em>here</em> rather than in a suppression list inside the test.
 *
 * <h2>Why a reason string, and why at the field</h2>
 *
 * <p>{@code MandatoryDeclarationRulesTest} fails the build when a guarded component carries no
 * declaration, so something has to mark these. A bare marker would answer the build and not the
 * reader: whoever next opens {@code NewPurchaseInvoiceLine} and finds a {@code requireNonNull} on a
 * component that is not {@code @Mandatory} would have to reconstruct why, and the obvious conclusion
 * — <em>somebody forgot</em> — is wrong and would be "fixed". A list of exemptions in the test file is
 * worse still: it is correct, invisible, and nowhere near the thing it describes.
 *
 * <h2>What it is NOT</h2>
 *
 * <p>Not a way to silence the rule. There are six of these on the whole surface as of 2026-08-03, and
 * every one is a field whose requirement genuinely depends on another field's value. Reaching for
 * this because the rule is inconvenient is the failure that makes a rule get deleted.
 *
 * <p>⚠️ <strong>OpenAPI cannot express what these fields actually are, and that is a real gap rather
 * than a limitation of this annotation.</strong> {@code NewPurchaseInvoiceLine} is a discriminated
 * union modelled as one flat record: five components of which at most three can ever be present,
 * selected by {@code type}. No {@code required} list describes that — it needs {@code oneOf} with a
 * discriminator. Recorded as a design item in {@code PROGRESS.md}; leaving these undeclared is
 * incomplete and true, which is the right side of that trade.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ConditionallyMandatory {

    /**
     * Why this component's guard is conditional, in the domain's own terms.
     *
     * <p>Written for someone reading the record, not for the build. "required only when
     * {@code type == INVENTORY}; forbidden when {@code type == EXPENSE}" is the shape; "conditional"
     * is not.
     */
    String value();
}
