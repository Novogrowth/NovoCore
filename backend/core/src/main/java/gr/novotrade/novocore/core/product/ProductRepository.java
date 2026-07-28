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
}
