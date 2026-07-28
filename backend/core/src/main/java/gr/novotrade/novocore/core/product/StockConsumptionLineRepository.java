package gr.novotrade.novocore.core.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.inventory.InventoryService}.
 */
interface StockConsumptionLineRepository extends JpaRepository<StockConsumptionLine, Long> {

    /**
     * Every consumption this lot has contributed to.
     *
     * <p>What the Goods Receipt reversal reads to find out whether a lot has been touched since it
     * arrived — the check that makes ADR 0008's "refused, not partially undone" enforceable rather
     * than aspirational.
     */
    List<StockConsumptionLine> findByLotIdOrderByIdAsc(long lotId);

    boolean existsByLotId(long lotId);
}
