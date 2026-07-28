package gr.novotrade.novocore.core.asset;

import gr.novotrade.novocore.core.api.asset.AssetStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through {@link gr.novotrade.novocore.core.api.asset.AssetService}.
 */
interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByOrderByNameAsc();

    List<Asset> findByStatusOrderByNameAsc(AssetStatus status);

    /** In use and with a rate set — what a depreciation run can actually charge. */
    List<Asset> findByStatusAndDepreciationRatePercentIsNotNullOrderByNameAsc(AssetStatus status);

    /** In use but still waiting for a statutory rate. The list that must not be forgotten. */
    List<Asset> findByStatusAndDepreciationRatePercentIsNullOrderByNameAsc(AssetStatus status);

    Optional<Asset> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
