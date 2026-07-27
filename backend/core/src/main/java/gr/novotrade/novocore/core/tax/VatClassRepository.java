package gr.novotrade.novocore.core.tax;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.tax.VatClassService}.
 *
 * <p>Note the absence of {@code findByRatePercent}. Two classes charge 4% with different legal
 * bases, so a rate does not identify a class and such a method would be right most of the time.
 */
interface VatClassRepository extends JpaRepository<VatClass, Long> {

    List<VatClass> findAllByOrderByRatePercentAscCodeAsc();

    List<VatClass> findByActiveTrueOrderByRatePercentAscCodeAsc();

    Optional<VatClass> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select v from VatClass v where v.reducedCounterpart is not null "
            + "order by v.ratePercent asc, v.code asc")
    List<VatClass> findWithAReducedCounterpart();

    /** Whichever class already claims this one as its island-reduced counterpart, if any. */
    @Query("select v from VatClass v where v.reducedCounterpart.id = :counterpartId")
    Optional<VatClass> findClaiming(long counterpartId);
}
