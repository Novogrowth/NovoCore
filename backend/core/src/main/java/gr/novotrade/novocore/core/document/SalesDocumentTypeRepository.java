package gr.novotrade.novocore.core.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.SalesDocumentTypeService}.
 */
interface SalesDocumentTypeRepository extends JpaRepository<SalesDocumentType, Long> {

    List<SalesDocumentType> findAllByOrderByDescriptionAsc();

    List<SalesDocumentType> findByActiveTrueOrderByDescriptionAsc();

    /**
     * Types whose stock behaviour is undecided — drafts.
     *
     * <p>Necessarily inactive, because the table refuses an active row with an undecided flag, and
     * a different thing from a retired type: the two look identical in a list and have entirely
     * different fixes.
     */
    List<SalesDocumentType> findByAffectsStockIsNullOrTransfersStockIsNullOrderByDescriptionAsc();

    Optional<SalesDocumentType> findByDescriptionIgnoreCase(String description);
}
