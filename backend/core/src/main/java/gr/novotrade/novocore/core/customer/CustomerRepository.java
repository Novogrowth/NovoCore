package gr.novotrade.novocore.core.customer;

import gr.novotrade.novocore.core.api.customer.CustomerSystemKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.customer.CustomerService}.
 */
interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    List<Customer> findByActiveTrueOrderByNameAsc();

    Optional<Customer> findByVatNumberIgnoreCase(String vatNumber);

    boolean existsByVatNumberIgnoreCase(String vatNumber);

    /** Q10's shared retail record, located by key rather than by an editable name. */
    Optional<Customer> findBySystemKey(CustomerSystemKey systemKey);

    // ---------------------------------------------------------------------------------------
    // Suggestive-only matching (brief §5), one criterion per query
    // ---------------------------------------------------------------------------------------
    // Three separate derived queries rather than one JPQL query with three optional parameters.
    // The single-query version is written more compactly but does not work: a named parameter that
    // appears only inside `:x IS NOT NULL` gives Hibernate nothing to infer a type from, it binds
    // the argument as `bytea`, and PostgreSQL then rejects `lower(bytea)` at runtime. Combining the
    // results in Java is unambiguous, and each of these three is obviously correct on its own.

    List<Customer> findByNameContainingIgnoreCaseOrderByNameAsc(String nameFragment);

    List<Customer> findByEmailIgnoreCaseOrderByNameAsc(String email);

    List<Customer> findByPhoneOrderByNameAsc(String phone);
}
