package gr.novotrade.novocore.core.sales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.sales.PaymentMethodService}.
 */
interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    /** ⚠️ Sort code, not insertion order — that is what the column is for. */
    List<PaymentMethod> findAllByOrderBySortCodeAsc();

    List<PaymentMethod> findByActiveTrueOrderBySortCodeAsc();

    Optional<PaymentMethod> findByAbbreviationIgnoreCase(String abbreviation);

    boolean existsByAbbreviationIgnoreCase(String abbreviation);

    boolean existsBySortCode(int sortCode);

    /**
     * Whether any sales invoice names this method — the predicate that freezes every field.
     *
     * <p>⚠️ <strong>A reversed invoice counts.</strong> It was recorded, it is in the journal, and
     * "recorded" is not "standing" — the same semantics {@code SalesDocumentSeriesRepository} uses
     * for a series, and stated here rather than left to be inferred from the absence of a filter.
     */
    @Query("select count(i) > 0 from SalesInvoice i where i.paymentMethodId = :id")
    boolean isNamedByARecordedDocument(@Param("id") long id);

    /** One query for a whole list's in-use flags, rather than one per row. */
    @Query("select distinct i.paymentMethodId from SalesInvoice i where i.paymentMethodId is not null")
    List<Long> idsNamedByARecordedDocument();
}
