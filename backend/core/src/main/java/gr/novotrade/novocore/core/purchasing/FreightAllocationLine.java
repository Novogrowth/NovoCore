package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
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
 * One lot's share of an allocated landed cost, split into the half that raised a carrying value and
 * the half that could not — <strong>Q18's answer, per lot</strong> (ADR 0010).
 *
 * <p><strong>The lot is a plain id.</strong> {@code InventoryLot} is package-private in the product
 * slice, which is the boundary ADR 0003 draws inside the core as well as around it. It is validated
 * and moved through {@code InventoryService}, the published interface an adapter would use, and the
 * foreign key still exists in the database.
 *
 * <p><strong>What is stored and what is not.</strong> The two posted halves are stored, because they
 * are what the entry says. Their sum is not: a total beside its own parts is the second copy of a
 * fact this schema keeps refusing to create. The basis the share was computed from is not stored
 * either, because the lot's received quantity and received cost are both frozen for its life, so it
 * is recomputable exactly — which is one of the reasons they are frozen. What <em>is</em> stored, and
 * could not be recovered any other way, is {@link #quantityRemainingAtAllocation}: what was left in
 * the lot at the moment this posted, which is both what the split was made from and what a reversal
 * is checked against.
 */
@Entity
@Table(name = "freight_allocation_line")
class FreightAllocationLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false)
    private FreightAllocation allocation;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(name = "quantity_remaining_at_allocation", nullable = false)
    private BigDecimal quantityRemainingAtAllocation;

    /** The share belonging to stock still on hand. Debited to Inventory against the lot. */
    @Column(name = "capitalised_amount", nullable = false)
    private BigDecimal capitalisedAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "capitalised_amount_currency", nullable = false, length = 3)
    private String capitalisedAmountCurrency;

    /** The share belonging to stock already gone. Debited to Landed cost variance. */
    @Column(name = "variance_amount", nullable = false)
    private BigDecimal varianceAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "variance_amount_currency", nullable = false, length = 3)
    private String varianceAmountCurrency;

    /** What this added to one unit of the lot. Exactly what a reversal takes back off. */
    @Column(name = "landed_unit_cost", nullable = false)
    private BigDecimal landedUnitCost;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "landed_unit_cost_currency", nullable = false, length = 3)
    private String landedUnitCostCurrency;

    /** For JPA only. */
    protected FreightAllocationLine() {
    }

    FreightAllocationLine(long lotId, Quantity quantityRemainingAtAllocation, Money capitalised,
            Money variance, UnitCost landedUnitCost) {
        this.lotId = lotId;
        this.quantityRemainingAtAllocation = quantityRemainingAtAllocation.value();
        this.capitalisedAmount = capitalised.amount();
        this.capitalisedAmountCurrency = capitalised.currency().getCurrencyCode();
        this.varianceAmount = variance.amount();
        this.varianceAmountCurrency = variance.currency().getCurrencyCode();
        this.landedUnitCost = landedUnitCost.value();
        this.landedUnitCostCurrency = landedUnitCost.currency().getCurrencyCode();
    }

    void attachTo(FreightAllocation owner, int position) {
        this.allocation = owner;
        this.lineNumber = position;
    }

    Long getId() {
        return id;
    }

    FreightAllocation getAllocation() {
        return allocation;
    }

    int getLineNumber() {
        return lineNumber;
    }

    Long getLotId() {
        return lotId;
    }

    Quantity getQuantityRemainingAtAllocation() {
        return Quantity.of(quantityRemainingAtAllocation);
    }

    Money getCapitalisedAmount() {
        return Money.of(capitalisedAmount, Currency.getInstance(capitalisedAmountCurrency));
    }

    Money getVarianceAmount() {
        return Money.of(varianceAmount, Currency.getInstance(varianceAmountCurrency));
    }

    /** This lot's whole share. Computed, for the reason the class comment gives. */
    Money getShare() {
        return getCapitalisedAmount().plus(getVarianceAmount());
    }

    UnitCost getLandedUnitCost() {
        return new UnitCost(landedUnitCost, Currency.getInstance(landedUnitCostCurrency));
    }
}
