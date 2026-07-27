package gr.novotrade.novocore.core.api.tax;

/** No such VAT exemption reason. */
public class VatExemptionReasonNotFoundException extends RuntimeException {

    public VatExemptionReasonNotFoundException(long id) {
        super("No VAT exemption reason with id " + id + ".");
    }

    public static VatExemptionReasonNotFoundException forCode(int code) {
        return new VatExemptionReasonNotFoundException(
                "No VAT exemption reason with AADE code " + code + ". Codes run roughly 1-31 "
                        + "with some numbers retired, so a gap is expected rather than an error.");
    }

    private VatExemptionReasonNotFoundException(String message) {
        super(message);
    }
}
