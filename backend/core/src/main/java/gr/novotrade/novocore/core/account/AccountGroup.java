package gr.novotrade.novocore.core.account;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The top level of the chart of accounts.
 *
 * <p>An entity rather than a text label on {@link Account} because group ordering is manual and
 * has to be stored somewhere. That is the entire justification — with alphabetical ordering a
 * label would have done.
 *
 * <p>Deliberately holds no collection of its accounts. The chart is assembled by the service from
 * two ordered queries rather than by navigating a mapped {@code OneToMany}, which keeps the read
 * to a predictable two statements instead of depending on how the fetch strategy happens to be
 * configured.
 */
@Entity
@Table(name = "account_group")
class AccountGroup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** For JPA only. */
    protected AccountGroup() {
    }

    AccountGroup(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void moveTo(int newDisplayOrder) {
        this.displayOrder = newDisplayOrder;
    }
}
