package gr.novotrade.novocore.core.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.product.ProductService}.
 */
interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderBySkuAsc();

    List<Product> findByActiveTrueOrderBySkuAsc();

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findByEan(String ean);

    boolean existsByEan(String ean);

    List<Product> findBySupplierIdOrderBySkuAsc(long supplierId);

    /** Bundles (Q11). Active and inactive alike, since a discontinued bundle is still a bundle. */
    List<Product> findByBundleTrueOrderBySkuAsc();

    /** Whether any product still refers to a unit, before it may be deactivated. */
    long countByUnitOfMeasureId(long unitOfMeasureId);
}
