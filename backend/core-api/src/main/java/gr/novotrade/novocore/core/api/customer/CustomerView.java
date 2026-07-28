package gr.novotrade.novocore.core.api.customer;

import gr.novotrade.novocore.core.api.tax.VatClassPrecedence;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * One customer, as everything outside the core sees it.
 *
 * <p><strong>No external system reference ids.</strong> No WooCommerce customer id, no Skroutz id,
 * no Prosvasis Go id — {@code CLAUDE.md} rule 2. The core knows only its own {@link #id()}, which
 * is brief §5's "own internal ID"; each adapter keeps its own mapping table.
 *
 * <p>No balance. What a customer owes is the sum of their journal lines against Accounts
 * receivable, computed on read, like every other balance in NovoCore.
 *
 * <p><strong>Three VAT-ish fields sit here and mean different things</strong>, which is worth
 * stating because they are easy to conflate:
 *
 * <ul>
 *   <li>{@link #vatStatus()} — the legal category this customer is in;
 *   <li>{@link #vatClassOverrideId()} — which <em>rate</em> their lines take, the middle level of
 *       {@link VatClassPrecedence} (invoice line beats customer beats product);
 *   <li>{@link #vatExemptionReasonId()} — the named article a supply to them is outside VAT under,
 *       which is what gets reported.
 * </ul>
 *
 * @param email a single address. Multi-value contact details were considered and rejected (Q8): one
 *     each is what the business actually has, and a one-to-many table would have to be joined,
 *     rendered and de-duplicated everywhere for a case that has not arisen.
 * @param vatNumber the ΑΦΜ or EU VAT number. Unique when present, because brief §5 makes it the
 *     authoritative identifier for matching — two customers cannot share one. Null for retail
 *     customers, who mostly have none. Never validated against VIES; that adapter is phase 7.
 * @param vatClassOverrideId a rate that beats the product's default for this customer's lines, or
 *     null to let the product decide. Nullable on purpose: an override is the exception, and a
 *     value copied onto every customer would quietly become the level that always wins.
 * @param vatExemptionReasonId required when {@link #vatStatus()} is {@link VatStatus#EXEMPT},
 *     optional otherwise.
 */
public record CustomerView(
        long id,
        String name,
        String email,
        String phone,
        String vatNumber,
        VatStatus vatStatus,
        Long vatClassOverrideId,
        Long vatExemptionReasonId,
        boolean active) {

    public CustomerView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(vatStatus, "vatStatus");
        if (vatStatus.requiresVatNumber() && vatNumber == null) {
            throw new IllegalArgumentException(
                    "Customer '" + name + "' is " + vatStatus + ", which is not meaningful "
                            + "without a VAT number: with no counterparty VAT number there is no "
                            + "reverse charge to apply, and the supply is a distance sale to a "
                            + "consumer instead.");
        }
        if (vatStatus.requiresExemptionReason() && vatExemptionReasonId == null) {
            throw new IllegalArgumentException(
                    "Customer '" + name + "' is " + vatStatus + ", so a VAT exemption reason must "
                            + "name the article it is exempt under.");
        }
    }

    public Optional<String> emailIfAny() {
        return Optional.ofNullable(email);
    }

    public Optional<String> phoneIfAny() {
        return Optional.ofNullable(phone);
    }

    public Optional<String> vatNumberIfAny() {
        return Optional.ofNullable(vatNumber);
    }

    /** The customer level of the VAT precedence rule — empty when the product's default stands. */
    public Optional<Long> vatClassOverride() {
        return Optional.ofNullable(vatClassOverrideId);
    }

    public Optional<Long> vatExemptionReason() {
        return Optional.ofNullable(vatExemptionReasonId);
    }
}
