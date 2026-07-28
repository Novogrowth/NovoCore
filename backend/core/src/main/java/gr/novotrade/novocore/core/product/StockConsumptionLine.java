package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * One lot's contribution to a consumption — brief §6's "one line per lot consumed".
 *
 * <p><strong>The unit cost is stored rather than read off the lot.</strong> Step 10 will allocate
 * landed costs onto a lot and move its unit cost, and this is what was actually costed out; recomputing
 * it later would give a different figure with nothing to say which one was historical. The same
 * argument that keeps an amount off {@code StockWriteOff} points the other way here, because there the
 * figure lives on the journal entry and here the entry aggregates several lots into one pair of lines
 * per lot — the per-lot cost is a fact about the consumption, not only about the posting.
 */
@Entity
@Table(name = "stock_consumption_line")
class StockConsumptionLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consumption_id", nullable = false)
    private StockConsumption consumption;

    /** Position within the consumption, so a listing is stable — V15's lesson about ordering. */
    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private InventoryLot lot;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "unit_cost_currency", nullable = false, length = 3)
    private String unitCostCurrency;

    /** For JPA only. */
    protected StockConsumptionLine() {
    }

    StockConsumptionLine(StockConsumption consumption, int lineNumber, InventoryLot lot,
            Quantity quantity, UnitCost unitCost) {
        this.consumption = consumption;
        this.lineNumber = lineNumber;
        this.lot = lot;
        this.quantity = quantity.value();
        this.unitCost = unitCost.value();
        this.unitCostCurrency = unitCost.currency().getCurrencyCode();
    }

    Long getId() {
        return id;
    }

    int getLineNumber() {
        return lineNumber;
    }

    InventoryLot getLot() {
        return lot;
    }

    Quantity getQuantity() {
        return Quantity.of(quantity);
    }

    UnitCost getUnitCost() {
        return new UnitCost(unitCost, Currency.getInstance(unitCostCurrency));
    }
}
