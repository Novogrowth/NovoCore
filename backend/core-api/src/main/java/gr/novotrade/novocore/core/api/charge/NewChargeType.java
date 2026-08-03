package gr.novotrade.novocore.core.api.charge;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * Request to add a chargeable fee type.
 *
 * @param incomeAccountId must be an {@code INCOME}-type account — see
 *     {@link ChargeTypeService#create}
 */
public record NewChargeType(@Mandatory String name, long defaultVatClassId, long incomeAccountId) {

    public NewChargeType {
        Objects.requireNonNull(name, "name");
    }
}
