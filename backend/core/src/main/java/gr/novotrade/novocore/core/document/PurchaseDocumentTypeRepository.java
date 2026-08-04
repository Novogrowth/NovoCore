package gr.novotrade.novocore.core.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.PurchaseDocumentTypeService}.
 */
interface PurchaseDocumentTypeRepository extends JpaRepository<PurchaseDocumentType, Long> {


    /**
     * ⚠️ Ordered by {@code sort_code}. That is what the column exists for — the previous default
     * ordered by description, which is the Greek alphabet and therefore arbitrary to the person
     * reading the list. See {@code V34}.
     */
    List<PurchaseDocumentType> findAllByOrderBySortCodeAsc();

    List<PurchaseDocumentType> findByActiveTrueOrderBySortCodeAsc();

    /**
     * Types whose stock behaviour is undecided — drafts.
     *
     * <p>Necessarily inactive, because the table refuses an active row with an undecided flag, and
     * a different thing from a retired type: the two look identical in a list and have entirely
     * different fixes.
     */
    List<PurchaseDocumentType> findByAffectsStockIsNullOrTransfersStockIsNullOrderBySortCodeAsc();

    /** Unique per table, so the ordering is deterministic. */
    boolean existsBySortCode(int sortCode);

    Optional<PurchaseDocumentType> findByDescriptionIgnoreCase(String description);
}
