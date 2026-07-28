package gr.novotrade.novocore.core.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached through {@link gr.novotrade.novocore.core.api.bundle.BundleService}, and by
 * {@code ProductServiceImpl} and {@code InventoryServiceImpl}, which share this slice — the former to
 * refuse deactivating a component an active bundle still needs, the latter to compute a bundle's
 * availability from its components.
 */
interface BundleComponentRepository extends JpaRepository<BundleComponent, Long> {

    List<BundleComponent> findByBundleIdOrderByComponentSkuAsc(long bundleProductId);

    List<BundleComponent> findByComponentIdOrderByBundleSkuAsc(long componentProductId);

    boolean existsByBundleId(long bundleProductId);

    /** Whether any bundle at all still lists this product. Read before a deactivation. */
    boolean existsByComponentId(long componentProductId);

    void deleteByBundleId(long bundleProductId);
}
