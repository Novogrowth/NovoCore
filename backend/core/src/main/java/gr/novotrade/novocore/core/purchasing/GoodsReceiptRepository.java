package gr.novotrade.novocore.core.purchasing;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService}.
 */
interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    List<GoodsReceipt> findBySupplierIdOrderByReceiptDateAscIdAsc(long supplierId);

    List<GoodsReceipt> findByReceiptDateBetweenOrderByReceiptDateAscIdAsc(
            LocalDate from, LocalDate to);

    Optional<GoodsReceipt> findByJournalEntryId(long journalEntryId);

    /** The document that reverses this one, if any. Stored one way, queried the other. */
    Optional<GoodsReceipt> findByReversalOfId(long receiptId);

    /** The batched form, so listing receipts does not become a query per row. */
    @Query("select r.reversalOfId, r.id from GoodsReceipt r where r.reversalOfId in :receiptIds")
    List<Object[]> findReversalPairs(Collection<Long> receiptIds);
}
