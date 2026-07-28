package gr.novotrade.novocore.core.supplier;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.supplier.SupplierService}.
 */
interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();

    List<Supplier> findByActiveTrueOrderByNameAsc();

    Optional<Supplier> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Optional<Supplier> findByVatNumberIgnoreCase(String vatNumber);

    boolean existsByVatNumberIgnoreCase(String vatNumber);

    // ---------------------------------------------------------------------------------------
    // Suggestive-only matching, one criterion per query
    // ---------------------------------------------------------------------------------------
    // Three derived queries rather than one JPQL query with three optional parameters, for the
    // reason spelled out in CustomerRepository: a named parameter appearing only inside
    // `:x IS NOT NULL` gets bound as `bytea`, and PostgreSQL rejects `lower(bytea)` at runtime.

    List<Supplier> findByNameContainingIgnoreCaseOrderByNameAsc(String nameFragment);

    List<Supplier> findByEmailIgnoreCaseOrderByNameAsc(String email);

    List<Supplier> findByPhoneOrderByNameAsc(String phone);
}
