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
 * @param systemKey non-null on a record NovoCore's own logic has to locate — today only the shared
 *     retail customer (Q10). A keyed record is protected: it cannot be deactivated, its VAT treatment
 *     is fixed, and it is refused on both sides of a merge. See {@link CustomerSystemKey}.
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
        CustomerSystemKey systemKey,
        boolean active) {

    /**
     * ⚠️ <strong>These are {@code IllegalArgumentException} on purpose, and that is not the
     * anti-pattern it looks like.</strong>
     *
     * <p>A {@code CustomerView} is a <em>response</em>: it is built only after the service has
     * accepted a change, so a view that cannot satisfy these invariants means <em>this codebase</em>
     * assembled an incoherent record — a programming error, which is exactly what the type means and
     * what {@code WebExceptionHandler} is right to withhold from a caller.
     *
     * <p><strong>What went wrong in backend queue item 4 was that they were the only place the two
     * system-record rules existed.</strong> A caller taking {@code PATCH …/vat-status} or
     * {@code PATCH …/vat-number} reached them directly and got a bare {@code 400 "Bad request."}
     * with a complete explanation discarded. Fixed in Q1 (2026-08-03) by checking both on the
     * caller's path in {@code CustomerServiceImpl}, where a caller's mistake is refused with
     * {@code InvalidCustomerException} and a 422 carrying its reason. These stayed, as the backstop
     * they correctly are.
     */
    public CustomerView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(vatStatus, "vatStatus");
        if (systemKey != null && vatStatus != VatStatus.DOMESTIC) {
            throw new IllegalArgumentException(
                    "Customer '" + name + "' carries system key " + systemKey + " and is "
                            + vatStatus + ". A system record's VAT treatment is fixed at DOMESTIC: "
                            + "it is not one identifiable party, so it cannot make a claim about a "
                            + "party's status.");
        }
        if (systemKey != null && vatNumber != null) {
            throw new IllegalArgumentException(
                    "Customer '" + name + "' carries system key " + systemKey + " and a VAT number. "
                            + "A shared anonymous record cannot hold one party's ΑΦΜ.");
        }
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

    /** The key this record is located by, when it is one NovoCore's own logic has to find. */
    public Optional<CustomerSystemKey> systemRecord() {
        return Optional.ofNullable(systemKey);
    }

    /**
     * True when this is a structural record rather than a real party — the shared retail customer.
     *
     * <p>Worth asking before offering an edit: its VAT treatment is fixed, it cannot be deactivated,
     * and it is refused on both sides of a merge.
     */
    public boolean isSystemRecord() {
        return systemKey != null;
    }

    /** True when this record may take part in a merge. Everything except a system record. */
    public boolean isMergeable() {
        return systemKey == null || systemKey.isMergeable();
    }
}
