package gr.novotrade.novocore.core.api.inventory;

/**
 * Who bought a serialized unit and on what — brief §5's customer/invoice link, as the thing that has
 * to be supplied before a unit can be marked {@code SOLD}.
 *
 * <p>Step 6 declared {@link SerializedUnitStatus#SOLD} and left it unreachable on purpose, refusing to
 * add a nullable customer id with no document behind it. This record is the alternative it was waiting
 * for: both halves together, required, so the status cannot be reached without them.
 */
public record SaleReference(long customerId, long salesInvoiceLineId) {

    public SaleReference {
        if (customerId <= 0) {
            throw new IllegalArgumentException(
                    "customerId must be a positive NovoCore id, got " + customerId);
        }
        if (salesInvoiceLineId <= 0) {
            throw new IllegalArgumentException(
                    "salesInvoiceLineId must be a positive NovoCore id, got " + salesInvoiceLineId
                            + ". A unit is marked sold by the line that sold it, which means the "
                            + "invoice has to exist first.");
        }
    }

    public static SaleReference of(long customerId, long salesInvoiceLineId) {
        return new SaleReference(customerId, salesInvoiceLineId);
    }

    @Override
    public String toString() {
        return "customer #" + customerId + " on invoice line #" + salesInvoiceLineId;
    }
}
