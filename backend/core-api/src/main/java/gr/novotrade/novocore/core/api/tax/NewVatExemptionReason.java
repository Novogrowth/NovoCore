package gr.novotrade.novocore.core.api.tax;

import java.util.Objects;

/**
 * Request to add an AADE VAT exemption reason.
 *
 * <p>Exists mainly so the seed can be loaded through the service and validated on the way in,
 * and so a reason AADE adds later can be entered without a code change. Operators should not be
 * inventing entries here — the list is AADE's.
 */
public record NewVatExemptionReason(
        int code,
        String description,
        String mydataCode,
        boolean inputVatDeductible) {

    public NewVatExemptionReason {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(mydataCode, "mydataCode");
    }
}
