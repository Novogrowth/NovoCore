package gr.novotrade.novocore.core.api.supplier;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * One supplier, as everything outside the core sees it.
 *
 * <p><strong>No external system reference ids.</strong> No Prosvasis Go supplier id, no
 * WooCommerce id — {@code CLAUDE.md} rule 2. Each adapter keeps its own mapping table from its
 * external id to this record's {@link #id()}.
 *
 * <p>No balance either. What we owe a supplier is the sum of their journal lines against Accounts
 * payable, computed on read once the ledger exists, for the same reason no account carries a
 * stored balance.
 *
 * @param vatNumber the ΑΦΜ or EU VAT number. Unique when present, because it is the authoritative
 *     identifier for matching (brief §5) — two suppliers cannot share one. Null is allowed: a
 *     supplier can be recorded before their VAT number is known. Never validated against VIES;
 *     that adapter is roadmap phase 7.
 * @param vatExemptionReasonId the article under which supplies from this supplier are outside VAT.
 *     Required when {@link #vatStatus()} is {@link VatStatus#EXEMPT}, optional otherwise.
 */
public record SupplierView(
        long id,
        @Mandatory String name,
        String email,
        String phone,
        String vatNumber,
        @Mandatory VatStatus vatStatus,
        Long vatExemptionReasonId,
        boolean active) {

    public SupplierView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(vatStatus, "vatStatus");
        if (vatStatus.requiresVatNumber() && vatNumber == null) {
            throw new IllegalArgumentException(
                    "Supplier '" + name + "' is " + vatStatus + ", which is not meaningful "
                            + "without a VAT number.");
        }
        if (vatStatus.requiresExemptionReason() && vatExemptionReasonId == null) {
            throw new IllegalArgumentException(
                    "Supplier '" + name + "' is " + vatStatus + ", so a VAT exemption reason "
                            + "must name the article it is exempt under.");
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

    public Optional<Long> vatExemptionReason() {
        return Optional.ofNullable(vatExemptionReasonId);
    }
}
