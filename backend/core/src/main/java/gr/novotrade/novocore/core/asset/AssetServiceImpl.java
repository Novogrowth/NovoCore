package gr.novotrade.novocore.core.asset;

import gr.novotrade.novocore.core.api.asset.AssetNotFoundException;
import gr.novotrade.novocore.core.api.asset.AssetService;
import gr.novotrade.novocore.core.api.asset.AssetStatus;
import gr.novotrade.novocore.core.api.asset.AssetView;
import gr.novotrade.novocore.core.api.asset.InvalidAssetException;
import gr.novotrade.novocore.core.api.asset.NewAsset;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asset register. No depreciation run and no disposal posting — both write journal entries, and
 * the journal arrives in step 7. What this provides is the register a run will read.
 */
@Service
class AssetServiceImpl implements AssetService {

    private static final String ENTITY_TYPE = "Asset";

    private final AssetRepository repository;
    private final AuditLogService auditLog;

    AssetServiceImpl(AssetRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetView> all() {
        return toViews(repository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetView> inUse() {
        return toViews(repository.findByStatusOrderByNameAsc(AssetStatus.IN_USE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetView> depreciable() {
        return toViews(repository
                .findByStatusAndDepreciationRatePercentIsNotNullOrderByNameAsc(AssetStatus.IN_USE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetView> withoutDepreciationRate() {
        return toViews(repository
                .findByStatusAndDepreciationRatePercentIsNullOrderByNameAsc(AssetStatus.IN_USE));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssetView> find(long id) {
        return repository.findById(id).map(AssetServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetView require(long id) {
        return find(id).orElseThrow(() -> new AssetNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssetView> findByCode(String code) {
        String normalised = optionalText(code);
        if (normalised == null) {
            return Optional.empty();
        }
        return repository.findByCodeIgnoreCase(normalised).map(AssetServiceImpl::toView);
    }

    @Override
    @Transactional
    public AssetView create(NewAsset request) {
        Objects.requireNonNull(request, "request");
        String name = requireText(request.name(), "Asset name");
        String code = optionalText(request.code());

        if (code != null && repository.existsByCodeIgnoreCase(code)) {
            throw new InvalidAssetException("An asset with code '" + code + "' already exists.");
        }
        requireValidRate(request.depreciationRatePercent());
        requireStartNotBeforeAcquisition(
                request.acquisitionDate(), request.depreciationStartDate());

        Asset saved = repository.save(new Asset(
                code, name, request.acquisitionDate(),
                percentOrNull(request.depreciationRatePercent()),
                request.depreciationStartDate()));

        auditLog.record("asset.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "name", name,
                "acquisitionDate", request.acquisitionDate().toString(),
                // Recorded explicitly as "(not set)" so that an asset created before its statutory
                // rate was known is distinguishable in the log from one created with a rate.
                "depreciationRatePercent", request.depreciationRatePercent() == null
                        ? "(not set)" : request.depreciationRatePercent().toString()));

        return toView(saved);
    }

    @Override
    @Transactional
    public AssetView rename(long id, String newName) {
        String name = requireText(newName, "Asset name");
        Asset asset = load(id);

        String previous = asset.getName();
        asset.rename(name);

        auditLog.record("asset.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("from", previous, "to", name));

        return toView(asset);
    }

    @Override
    @Transactional
    public AssetView changeDepreciationRate(long id, Rate ratePercent) {
        Asset asset = load(id);
        requireValidRate(ratePercent);

        BigDecimal previous = asset.getDepreciationRatePercent();
        asset.changeDepreciationRate(percentOrNull(ratePercent));

        auditLog.record("asset.depreciation-rate-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", asset.getName(),
                "from", previous == null ? "(not set)" : previous.toPlainString(),
                "to", ratePercent == null ? "(cleared)" : ratePercent.toString()));

        return toView(asset);
    }

    @Override
    @Transactional
    public AssetView changeDepreciationStartDate(long id, LocalDate depreciationStartDate) {
        Asset asset = load(id);
        requireStartNotBeforeAcquisition(asset.getAcquisitionDate(), depreciationStartDate);

        asset.changeDepreciationStartDate(depreciationStartDate);

        auditLog.record("asset.depreciation-start-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of(
                        "name", asset.getName(),
                        "depreciationStartDate", depreciationStartDate == null
                                ? "(acquisition date)" : depreciationStartDate.toString()));

        return toView(asset);
    }

    @Override
    @Transactional
    public AssetView dispose(long id, LocalDate disposalDate) {
        Objects.requireNonNull(disposalDate, "disposalDate");
        Asset asset = load(id);

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            // Refused rather than overwritten: a second disposal date would silently move the
            // period the asset left in, which is the period its gain or loss belongs to.
            throw new InvalidAssetException(
                    "Asset '" + asset.getName() + "' was already disposed of on "
                            + asset.getDisposalDate() + ". Reinstate it first if that date is "
                            + "wrong, so the correction is visible rather than overwritten.");
        }
        if (disposalDate.isBefore(asset.getAcquisitionDate())) {
            throw new InvalidAssetException(
                    "Asset '" + asset.getName() + "' cannot be disposed of on " + disposalDate
                            + ", before it was acquired on " + asset.getAcquisitionDate() + ".");
        }

        asset.dispose(disposalDate);

        auditLog.record("asset.disposed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", asset.getName(),
                "disposalDate", disposalDate.toString()));

        return toView(asset);
    }

    @Override
    @Transactional
    public AssetView reinstate(long id) {
        Asset asset = load(id);
        if (asset.getStatus() != AssetStatus.DISPOSED) {
            throw new InvalidAssetException(
                    "Asset '" + asset.getName() + "' is " + asset.getStatus()
                            + ", so there is no disposal to undo.");
        }

        LocalDate previousDisposal = asset.getDisposalDate();
        asset.reinstate();

        auditLog.record("asset.reinstated", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", asset.getName(),
                "previousDisposalDate", previousDisposal.toString()));

        return toView(asset);
    }

    private Asset load(long id) {
        return repository.findById(id).orElseThrow(() -> new AssetNotFoundException(id));
    }

    /**
     * Null is permitted and means "the statutory rate is not known yet".
     *
     * <p>Since step 15a, {@link Rate} refuses anything strictly between 0 and 1, anything above 100,
     * and anything finer than six decimals — the factor-of-100 trap, guarded once for every rate in
     * the system instead of separately here and on {@code VatClassView}.
     *
     * <p>What is left is the one rule that is genuinely an asset's own: <strong>zero is not a
     * depreciation rate.</strong> {@code Rate} has to permit it, because the zero-rated VAT class
     * is real; an asset that never depreciates is what a null rate already says, and two ways to
     * say it means every report has to handle both. The database says the same in
     * {@code asset_depreciation_rate_is_a_percentage}, which excludes zero for that reason.
     */
    private static void requireValidRate(Rate ratePercent) {
        if (ratePercent == null) {
            return;
        }
        if (ratePercent.isZero()) {
            throw new InvalidAssetException(
                    "A depreciation rate of zero is not a rate. An asset that never depreciates is "
                            + "what an unset rate already says, so leave it unset rather than "
                            + "recording a second way to mean the same thing.");
        }
    }

    /**
     * The entity still stores a plain {@code BigDecimal}, because the column is a
     * {@code numeric(19,6)} and a JPA converter would buy nothing here — {@link Rate} normalises on
     * construction, so what goes in is already at the schema's scale.
     */
    private static BigDecimal percentOrNull(Rate rate) {
        return rate == null ? null : rate.percent();
    }

    private static Rate rateOrNull(BigDecimal percent) {
        return percent == null ? null : Rate.of(percent);
    }

    private static void requireStartNotBeforeAcquisition(
            LocalDate acquisitionDate, LocalDate depreciationStartDate) {
        if (depreciationStartDate != null && depreciationStartDate.isBefore(acquisitionDate)) {
            throw new InvalidAssetException(
                    "Depreciation cannot start on " + depreciationStartDate + ", before the asset "
                            + "was acquired on " + acquisitionDate + ".");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidAssetException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static List<AssetView> toViews(List<Asset> assets) {
        return assets.stream().map(AssetServiceImpl::toView).toList();
    }

    private static AssetView toView(Asset asset) {
        return new AssetView(
                asset.getId(),
                asset.getCode(),
                asset.getName(),
                asset.getAcquisitionDate(),
                rateOrNull(asset.getDepreciationRatePercent()),
                asset.getDepreciationStartDate(),
                asset.getStatus(),
                asset.getDisposalDate());
    }
}
