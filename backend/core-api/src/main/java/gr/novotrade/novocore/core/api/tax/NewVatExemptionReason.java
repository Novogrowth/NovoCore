package gr.novotrade.novocore.core.api.tax;

import gr.novotrade.novocore.core.api.shared.Required;
import java.util.Objects;

/**
 * Request to add an AADE VAT exemption reason.
 *
 * <p>Exists mainly so the seed can be loaded through the service and validated on the way in,
 * and so a reason AADE adds later can be entered without a code change. Operators should not be
 * inventing entries here — the list is AADE's.
 *
 * @param mydataCode may be null where no myDATA mapping exists for the reason, as is the case for
 *     the OSS and IOSS entries. Null is the honest value there; a composed substitute would be a
 *     fabricated value that later gets transmitted.
 */
public record NewVatExemptionReason(
        int code,
        String description,
        String mydataCode,
        Boolean inputVatDeductible) {

    public NewVatExemptionReason {
        Required.field(inputVatDeductible, "inputVatDeductible");
        Objects.requireNonNull(description, "description");
    }

    /** A reason our invoicing system has no myDATA mapping for. */
    public static NewVatExemptionReason withoutMydataCode(
            int code, String description, boolean inputVatDeductible) {
        return new NewVatExemptionReason(code, description, null, inputVatDeductible);
    }
}
