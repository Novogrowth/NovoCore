package gr.novotrade.novocore.core.asset;

import gr.novotrade.novocore.core.api.asset.AssetStatus;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One fixed asset — the sub-ledger behind {@code Fixed assets at cost} and
 * {@code Fixed assets accumulated depreciation}.
 *
 * <p><strong>There is no cost field here, and that is deliberate.</strong> Both control accounts
 * declare {@code ASSET} as their sub-ledger, so every posting to them names its asset; cost and
 * accumulated depreciation are therefore sums of journal lines, computed on read. A stored
 * acquisition cost would be a second copy of a figure the ledger already holds, and the two would
 * part company at the first correcting entry — the same reasoning that keeps a running balance off
 * {@code Account}.
 *
 * <p>Straight-line only (brief §5), so no method field. No useful life either: for straight-line it
 * is {@code 100 / rate}, and two columns that must agree are two columns that can disagree.
 */
@Entity
@Table(name = "asset")
class Asset extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The operator's own asset tag, if they use one. Unique when present. */
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    /**
     * Annual straight-line rate as a percentage — 10% is {@code 10.000000}, not {@code 0.1}.
     *
     * <p>Nullable, and null is a meaningful value: the statutory rate for the asset's category has
     * not been supplied yet. A guessed rate produces a depreciation charge that looks plausible and
     * is wrong in filed accounts, so the absence is recorded rather than papered over.
     */
    @Column(name = "depreciation_rate_percent")
    private BigDecimal depreciationRatePercent;

    /** Null means "the same as {@link #acquisitionDate}", which is the ordinary case. */
    @Column(name = "depreciation_start_date")
    private LocalDate depreciationStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssetStatus status;

    /** Present exactly when {@link #status} is {@code DISPOSED}; the database enforces it. */
    @Column(name = "disposal_date")
    private LocalDate disposalDate;

    /** For JPA only. */
    protected Asset() {
    }

    Asset(String code, String name, LocalDate acquisitionDate, BigDecimal depreciationRatePercent,
            LocalDate depreciationStartDate) {
        this.code = code;
        this.name = name;
        this.acquisitionDate = acquisitionDate;
        this.depreciationRatePercent = depreciationRatePercent;
        this.depreciationStartDate = depreciationStartDate;
        this.status = AssetStatus.IN_USE;
        this.disposalDate = null;
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    BigDecimal getDepreciationRatePercent() {
        return depreciationRatePercent;
    }

    LocalDate getDepreciationStartDate() {
        return depreciationStartDate;
    }

    AssetStatus getStatus() {
        return status;
    }

    LocalDate getDisposalDate() {
        return disposalDate;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeDepreciationRate(BigDecimal newRatePercent) {
        this.depreciationRatePercent = newRatePercent;
    }

    void changeDepreciationStartDate(LocalDate newStartDate) {
        this.depreciationStartDate = newStartDate;
    }

    /** Status and disposal date move together — the database refuses either without the other. */
    void dispose(LocalDate newDisposalDate) {
        this.status = AssetStatus.DISPOSED;
        this.disposalDate = newDisposalDate;
    }

    void reinstate() {
        this.status = AssetStatus.IN_USE;
        this.disposalDate = null;
    }
}
