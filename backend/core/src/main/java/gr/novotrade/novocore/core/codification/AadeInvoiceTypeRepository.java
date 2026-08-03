package gr.novotrade.novocore.core.codification;

import gr.novotrade.novocore.core.api.codification.AadeInvoiceGroup;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService}.
 *
 * <p>⚠️ Ordering is by {@code id} rather than by {@code code}, and that is not laziness. The codes
 * are dotted strings — {@code "1.1"}, {@code "13.30"}, {@code "4"} — so a text sort puts
 * {@code "10.1"} before {@code "2.1"} and {@code "13.31"} before {@code "13.4"}, which is
 * nonsensical in a picker. The seed inserts them in the XSD's own enumeration order, which is also
 * annex 8.1's reading order, so ascending id <em>is</em> the authority's order.
 */
interface AadeInvoiceTypeRepository extends JpaRepository<AadeInvoiceType, Long> {

    List<AadeInvoiceType> findAllByOrderByIdAsc();

    List<AadeInvoiceType> findByActiveTrueOrderByIdAsc();

    List<AadeInvoiceType> findByInvoiceGroupOrderByIdAsc(AadeInvoiceGroup group);

    List<AadeInvoiceType> findByInvoiceGroupInOrderByIdAsc(Collection<AadeInvoiceGroup> groups);

    Optional<AadeInvoiceType> findByCodeIgnoreCase(String code);
}
