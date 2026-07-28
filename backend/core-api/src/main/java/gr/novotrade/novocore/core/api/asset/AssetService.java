package gr.novotrade.novocore.core.api.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The fixed asset register — the sub-ledger behind {@code Fixed assets at cost} and
 * {@code Fixed assets accumulated depreciation}.
 *
 * <p><strong>The register only.</strong> There is no depreciation run here and no disposal posting:
 * both write journal entries, and the journal does not exist until step 7. What this gives step 7 is
 * a list of assets, each with the rate and dates a run needs, and {@link #depreciable()} to ask for
 * the ones a run can actually charge.
 *
 * <p><strong>Rates are set per asset, by hand.</strong> Deriving a default from the asset's ΕΛΠ
 * account mapping was considered and deferred; an asset-level rate has to exist regardless, since a
 * specific asset can legitimately differ from its category. The statutory rates themselves are
 * pending the accountant, so {@link #withoutDepreciationRate()} exists to find the assets still
 * waiting for one instead of letting them be forgotten.
 */
public interface AssetService {

    /** Every asset, in use and disposed, by name. */
    List<AssetView> all();

    /** Assets still in service. */
    List<AssetView> inUse();

    /**
     * Assets a depreciation run can charge — in use, and with a rate set.
     *
     * <p>This is what step 7's run should iterate. The ones it excludes are exactly the ones that
     * need reporting rather than skipping silently; {@link #withoutDepreciationRate()} names them.
     */
    List<AssetView> depreciable();

    /**
     * Assets in use whose statutory depreciation rate has not been supplied yet.
     *
     * <p>Exists so that "we never filled in the rates" is a question the system can answer, rather
     * than a discovery made when the depreciation charge for the year comes out too low.
     */
    List<AssetView> withoutDepreciationRate();

    Optional<AssetView> find(long id);

    /** @throws AssetNotFoundException if absent */
    AssetView require(long id);

    /** By the operator's own asset code or tag, unique when present. */
    Optional<AssetView> findByCode(String code);

    /**
     * Adds an asset to the register.
     *
     * @throws InvalidAssetException if the name is blank, the code duplicates another asset, the
     *     rate is not a percentage above 0 and up to 100, or the depreciation start date precedes
     *     the acquisition date
     */
    AssetView create(NewAsset request);

    /** @throws InvalidAssetException if the name is blank */
    AssetView rename(long id, String newName);

    /**
     * Sets or clears the annual straight-line depreciation rate, as a percentage.
     *
     * <p>Clearing it is permitted, and means "this rate was wrong and the right one is not yet
     * known" — which is a more honest state to leave an asset in than a figure somebody guessed.
     *
     * @throws InvalidAssetException if the rate is not above 0 and up to 100
     */
    AssetView changeDepreciationRate(long id, BigDecimal ratePercent);

    /**
     * Sets or clears the date depreciation begins, when it differs from acquisition.
     *
     * @throws InvalidAssetException if it precedes the acquisition date
     */
    AssetView changeDepreciationStartDate(long id, LocalDate depreciationStartDate);

    /**
     * Records that the asset has been disposed of, which stops depreciation.
     *
     * <p>Records the fact only. Derecognising cost and accumulated depreciation and recognising the
     * gain or loss are postings, and belong with the ledger.
     *
     * @throws InvalidAssetException if the date precedes acquisition, or the asset is already
     *     disposed of — a second disposal date would silently overwrite the period it left in
     */
    AssetView dispose(long id, LocalDate disposalDate);

    /**
     * Undoes a disposal recorded in error, putting the asset back in service.
     *
     * <p>Present because there is no delete: an asset marked disposed by mistake would otherwise be
     * unrecoverable, and creating a replacement record would split its history across two ids.
     *
     * @throws InvalidAssetException if the asset is not disposed of
     */
    AssetView reinstate(long id);
}
