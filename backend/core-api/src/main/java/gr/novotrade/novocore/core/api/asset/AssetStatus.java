package gr.novotrade.novocore.core.api.asset;

/**
 * Where a fixed asset is in its life.
 *
 * <p>A status rather than the {@code active} boolean used by the lookup entities elsewhere in the
 * core, because for an asset those would be two different facts wearing one name: an asset can stop
 * being used without having been disposed of, and the accounting consequences are not the same.
 * Deactivating a VAT class means "stop offering this"; disposing of an asset means derecognising it
 * from the balance sheet.
 *
 * <p><strong>Fully depreciated is not a status here.</strong> It is derived — accumulated
 * depreciation having reached cost — and both of those figures are sums of journal lines rather than
 * stored fields, so a stored flag could disagree with the ledger it is supposed to summarise.
 */
public enum AssetStatus {

    /** In service. Depreciation runs against it. */
    IN_USE,

    /**
     * Sold, scrapped or otherwise gone. Depreciation stops; a disposal date is required.
     *
     * <p>The <em>posting</em> of a disposal — derecognising cost and accumulated depreciation and
     * recognising any gain or loss — is ledger work and does not exist yet. This records the fact
     * so that a depreciation run cannot keep charging an asset that has left the building.
     */
    DISPOSED;

    /** True when a depreciation run should still charge this asset. */
    public boolean depreciates() {
        return this == IN_USE;
    }
}
