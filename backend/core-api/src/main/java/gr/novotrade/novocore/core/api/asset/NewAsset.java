package gr.novotrade.novocore.core.api.asset;

import gr.novotrade.novocore.core.api.shared.Rate;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Request to add a fixed asset.
 *
 * <p>No acquisition cost. That is not an omission — see {@link AssetView} — the cost is whatever the
 * journal lines against {@code Fixed assets at cost} for this asset add up to, so it arrives with
 * the purchase posting rather than with the register entry.
 *
 * @param depreciationRatePercent may be null, meaning the statutory rate for this asset's category
 *     is not yet known. Null is the correct value in that case and the entity is still worth
 *     creating: the asset exists whether or not its rate has been confirmed, and a depreciation run
 *     will skip and report it rather than guessing.
 */
public record NewAsset(
        String code,
        String name,
        LocalDate acquisitionDate,
        Rate depreciationRatePercent,
        LocalDate depreciationStartDate) {

    public NewAsset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(acquisitionDate, "acquisitionDate");
    }

    /** An asset acquired and placed in service on the same date, with its rate still to come. */
    public static NewAsset awaitingRate(String name, LocalDate acquisitionDate) {
        return new NewAsset(null, name, acquisitionDate, null, null);
    }
}
