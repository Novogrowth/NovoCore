package gr.novotrade.novocore.core.web.asset;

import gr.novotrade.novocore.core.api.asset.AssetService;
import gr.novotrade.novocore.core.api.asset.AssetStatus;
import gr.novotrade.novocore.core.api.asset.AssetView;
import gr.novotrade.novocore.core.api.asset.NewAsset;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fixed asset register.
 *
 * <h2>A register, not a valuation</h2>
 *
 * <p>Asset carries <strong>no monetary field at all</strong>. Both fixed-asset control accounts
 * declare {@code ASSET} as their sub-ledger, so cost and accumulated depreciation are sums of
 * journal lines and an asset's carrying value is a ledger question.
 *
 * <p>Which is why <strong>there is no carrying-value route here</strong>. That figure is
 * {@code JournalService.subLedgerBalanceOf}, and reading it means reading postings —
 * {@link Section#JOURNAL}, which is close to granting everything. Exposing it on the asset route
 * would make this a second, weaker path to ledger data, which is the exact failure Q44's access-path
 * decision exists to prevent on the email outbox. Same principle, different door.
 *
 * <h2>The depreciation rate is nullable, and that is the register's real state</h2>
 *
 * <p>The statutory rates per asset category are still pending the accountant. Null means "not yet
 * known" — nothing was guessed and no category table was invented — and
 * {@code /without-depreciation-rate} exists so that stops being forgettable.
 */
@RestController
@Requires(section = Section.FIXED_ASSETS)
class AssetController {

    private final AssetService assets;

    AssetController(AssetService assets) {
        this.assets = assets;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/assets", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AssetView> assets(@RequestParam(required = false) AssetStatus status) {
        return ListResponse.of(
                status == AssetStatus.IN_USE ? assets.inUse() : assets.all());
    }

    /** Assets that can actually be depreciated — in use, and with a rate recorded. */
    @GetMapping(path = "/api/assets/depreciable", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AssetView> depreciable() {
        return ListResponse.of(assets.depreciable());
    }

    /**
     * Assets still waiting for their statutory rate.
     *
     * <p>Its own path rather than a query filter, because this is a list somebody opens on purpose:
     * it is the outstanding question with the accountant, expressed as data.
     */
    @GetMapping(path = "/api/assets/without-depreciation-rate",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AssetView> withoutDepreciationRate() {
        return ListResponse.of(assets.withoutDepreciationRate());
    }

    @GetMapping(path = "/api/assets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    AssetView asset(@PathVariable long id) {
        return assets.require(id);
    }

    @GetMapping(path = "/api/assets/by-code/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AssetView> byCode(@PathVariable String code) {
        return ListResponse.of(assets.findByCode(code).map(List::of).orElseGet(List::of));
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/assets",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.FIXED_ASSETS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    AssetView create(@RequestBody NewAsset request) {
        return assets.create(request);
    }

    @PatchMapping(path = "/api/assets/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.FIXED_ASSETS, level = AccessLevel.FULL)
    AssetView rename(@PathVariable long id, @RequestBody NameRequest request) {
        return assets.rename(id, request.name());
    }

    /**
     * The annual straight-line rate, as a percentage.
     *
     * <p><strong>Bounded 1–100, and the lower bound is the load-bearing half.</strong> A plain 0–100
     * range cannot catch {@code 0.1} written for 10%: it sits comfortably inside, and the charge
     * would be a hundred times too small every year with nothing complaining. 1% is a hundred-year
     * life, which no statutory category has.
     *
     * <p>So the number here is {@code 10} for 10%, never {@code 0.1}. It is a plain JSON number
     * rather than a string, unlike amounts and quantities — a rate is not money, its scale is not a
     * cent, and no arithmetic downstream depends on the client's own representation of it.
     */
    @PatchMapping(path = "/api/assets/{id}/depreciation-rate",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.FIXED_ASSETS, level = AccessLevel.FULL)
    AssetView changeDepreciationRate(
            @PathVariable long id, @RequestBody DepreciationRateRequest request) {
        return assets.changeDepreciationRate(id, request.ratePercent());
    }

    /** For an asset acquired before it was placed in service. */
    @PatchMapping(path = "/api/assets/{id}/depreciation-start-date",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.FIXED_ASSETS, level = AccessLevel.FULL)
    AssetView changeDepreciationStartDate(
            @PathVariable long id, @RequestBody DepreciationStartDateRequest request) {
        return assets.changeDepreciationStartDate(id, request.depreciationStartDate());
    }

    /**
     * Disposal. A {@code POST} to a sub-resource, not a status {@code PATCH}.
     *
     * <p>The disposal date is required exactly when the asset is disposed and refused otherwise — a
     * definitional pairing enforced by CHECK constraint, not a field that happens to accompany a
     * status. Modelling it as an event says that; a status field with an optional date does not.
     */
    @PostMapping(path = "/api/assets/{id}/disposal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.FIXED_ASSETS, level = AccessLevel.FULL)
    AssetView dispose(@PathVariable long id, @RequestBody DisposalRequest request) {
        return assets.dispose(id, request.disposalDate());
    }

    /** Undoes a disposal recorded in error, clearing the date with it. */
    @PostMapping(path = "/api/assets/{id}/reinstatement",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.FIXED_ASSETS, level = AccessLevel.FULL)
    AssetView reinstate(@PathVariable long id) {
        return assets.reinstate(id);
    }

    // -------------------------------------------------------------------------------------------

    record NameRequest(String name) {
    }

    /** A percentage: 10 means 10%. Null means the statutory rate is still unknown. */
    record DepreciationRateRequest(Rate ratePercent) {
    }

    record DepreciationStartDateRequest(LocalDate depreciationStartDate) {
    }

    record DisposalRequest(LocalDate disposalDate) {

        DisposalRequest {
            // Required exactly when the asset is disposed — the definitional pairing this route
            // exists to record, so a disposal naming no date is not a disposal.
            Required.field(disposalDate, "disposalDate");
        }
    }
}
