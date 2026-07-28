package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Objects;
import java.util.Optional;

/**
 * One individually tracked item within a lot — brief §5's Unit. A specific espresso machine, by
 * serial number.
 *
 * <p><strong>Why these are not pooled.</strong> Brief §5 states the exception plainly: a
 * count correction or write-off on a serialized item refers to that unit and <em>its own actual
 * cost</em>, with no FIFO logic. FIFO exists because interchangeable units cannot be told apart;
 * these can, so averaging or queueing them would be inventing an approximation where the real answer
 * is available.
 *
 * <p><strong>The location is on the unit, not on the lot.</strong> Three identical machines received
 * together are one lot, and one of them going out to a repair while the other two stay on the shelf
 * is an ordinary Tuesday. A single location on the lot cannot express that without splitting the lot,
 * so for serial-tracked stock the lot carries no location at all and each unit carries its own. The
 * rule across both: location lives wherever the quantity does.
 *
 * @param serialNumber the manufacturer's serial, unique across all stock — see
 *     {@code InventoryService.findUnitBySerialNumber} for why that is a stronger claim than the world
 *     strictly supports, and why it is still the right one
 * @param unitCost this unit's own cost, which is its lot's. Carried on the view so a serialized
 *     write-off or sale has the real figure to hand without going back for the lot.
 * @param soldToCustomerId brief §5's customer link on a sold unit — the step 6 obligation, discharged
 *     in step 9. Present exactly when the status is {@link SerializedUnitStatus#SOLD}, biconditionally
 *     and by CHECK: step 6 deliberately carried no nullable customer id early, because a unit marked
 *     sold to somebody with no document behind it is a claim nothing can substantiate.
 * @param soldOnInvoiceLineId the sales invoice line that sold it. Moves with the customer, always.
 */
public record SerializedUnitView(
        long id,
        long lotId,
        long productId,
        String serialNumber,
        SerializedUnitStatus status,
        StockLocation location,
        UnitCost unitCost,
        Long soldToCustomerId,
        Long soldOnInvoiceLineId) {

    public SerializedUnitView {
        Objects.requireNonNull(serialNumber, "serialNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(unitCost, "unitCost");
        if ((status == SerializedUnitStatus.SOLD) != (soldToCustomerId != null)) {
            throw new IllegalArgumentException(
                    "Unit '" + serialNumber + "' is " + status + " and "
                            + (soldToCustomerId == null ? "names no buyer" : "names a buyer")
                            + ". The two are one fact: brief §5 requires the customer and invoice on "
                            + "a sold unit, and a buyer on a unit that was not sold is a claim with "
                            + "no document behind it.");
        }
        if ((soldToCustomerId == null) != (soldOnInvoiceLineId == null)) {
            throw new IllegalArgumentException(
                    "Unit '" + serialNumber + "' has half a sale link. The customer and the invoice "
                            + "line move together.");
        }
    }

    /** True when this unit still counts towards stock on hand. */
    public boolean isOnHand() {
        return status.isOnHand();
    }

    /** True when this unit is on hand somewhere it may be sold from (Q7). */
    public boolean isSellable() {
        return isOnHand() && location.isSellable();
    }

    /** Who bought it — brief §5's warranty lookup, from a serial number to a customer. */
    public Optional<Long> soldTo() {
        return Optional.ofNullable(soldToCustomerId);
    }

    public Optional<Long> soldOn() {
        return Optional.ofNullable(soldOnInvoiceLineId);
    }
}
