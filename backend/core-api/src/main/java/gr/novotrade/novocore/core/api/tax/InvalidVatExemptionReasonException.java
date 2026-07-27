package gr.novotrade.novocore.core.api.tax;

/** A requested VAT exemption reason change is not allowed — a duplicate code or myDATA string. */
public class InvalidVatExemptionReasonException extends RuntimeException {

    public InvalidVatExemptionReasonException(String message) {
        super(message);
    }
}
