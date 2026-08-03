package gr.novotrade.novocore.core.api.tax;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.util.Objects;

/**
 * Request to add a VAT class.
 *
 * <p>This exists because VAT classes are a runtime-editable lookup, not an enum: a new rate must
 * be addable through the application without a code change and without a migration. That is the
 * whole reason the table is data rather than a Java enum, so the create path is a first-class
 * operation rather than something only the seed does.
 *
 * @param ratePercent a percentage — 24% is {@code Rate.of("24")}, not {@code 0.24}
 */
public record NewVatClass(
        @Mandatory String code,
        @Mandatory String description,
        @Mandatory Rate ratePercent) {

    public NewVatClass {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(ratePercent, "ratePercent");
    }
}
