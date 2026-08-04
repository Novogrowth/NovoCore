package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;
import java.util.Optional;

/**
 * A way a customer pays — the presentation half of a {@link SettlementMethod}.
 *
 * <h2>⚠️ This exists because of a scoping error, and the distinction is worth keeping</h2>
 *
 * <p>The owner's original nine-table specification asked for
 * {@code Τρόποι πληρωμής [ID, abbreviation, description, active/inactive, myDATA code]}. When it was
 * established that {@link SettlementMethod} is a Java enum, that was carried into R2's scope as
 * "it lives on an enum, so there is nothing to edit" — and payment methods got no screen at all,
 * while delivery methods, a near-identical row in the same specification, got a full CRUD one.
 *
 * <p><strong>The enum decision was right for the wrong scope.</strong> Adding a payment method
 * genuinely needs code — each value carries an {@code AccountSystemKey}, {@code settlesImmediately}
 * and {@code subjectToCashLimit}, and no form can supply those — so <strong>no create</strong> is
 * correct and stays. It never justified no screen.
 *
 * <h2>⚠️ THE myDATA CODE IS READ FROM THE ENUM AND IS NOT STORED</h2>
 *
 * <p>The brief for this table said to carry the code as a column. It does not, because the premise
 * was wrong: <strong>the codes have been on the enum since it was written.</strong> "None of these
 * fields exists on an enum" was true of abbreviation, description and active, and false of the code.
 *
 * <p>Storing it would create a second record of one thing — exactly what {@code PaymentMethodIT}'s
 * drift test exists to prevent — and would then need a drift test of its own. So it is resolved from
 * the enum when this view is built. <strong>There is nothing to disagree.</strong>
 *
 * @param method the {@link SettlementMethod} constant. The identity: there is no surrogate id,
 *     because a second identifier would be a second thing to keep in step.
 * @param mydataPaymentCode ⚠️ <strong>Read from the enum, never stored.</strong> Empty for
 *     {@code ACS_COD}, {@code PAYPAL} and {@code STRIPE}, whose codes are genuinely open and are not
 *     invented here.
 * @param settlesImmediately behaviour, read from the enum. Shown so an operator can see why a method
 *     cannot simply be added, and never editable here.
 * @param subjectToCashLimit behaviour, read from the enum. Same.
 * @param sortCode ⚠️ Ordering only, exactly as on the document reference tables — eight options in
 *     enum-declaration order is not a sensible list for somebody picking while recording a sale.
 *     Freely editable; see {@code V34} for the full argument.
 */
public record PaymentMethodView(
        @Mandatory SettlementMethod method,
        @Mandatory String abbreviation,
        @Mandatory String description,
        Integer mydataPaymentCode,
        boolean settlesImmediately,
        boolean subjectToCashLimit,
        int sortCode,
        boolean active) {

    public PaymentMethodView {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(abbreviation, "abbreviation");
        Objects.requireNonNull(description, "description");
    }

    /**
     * Empty where AADE's code for this method is still open.
     *
     * <p>Safe on a serialised record for the reason {@code CLAUDE.md} records after R1a: an
     * {@code Optional}-returning {@code …IfAny()} cannot throw, unlike the derived accessor that
     * answered {@code 500} for a whole codification.
     */
    public Optional<Integer> mydataPaymentCodeIfAny() {
        return Optional.ofNullable(mydataPaymentCode);
    }
}
