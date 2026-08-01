package gr.novotrade.novocore.core.security;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.security.RoleService}.
 */
interface RoleRepository extends JpaRepository<Role, Long>,
        JpaSpecificationExecutor<Role> {

    List<Role> findAllByOrderByNameAsc();

    List<Role> findByActiveTrueOrderByNameAsc();

    Optional<Role> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
