package gr.novotrade.novocore.core.codification;

import gr.novotrade.novocore.core.api.codification.AadeInvoiceGroup;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of the AADE myDATA invoice-type codification.
 *
 * <p>There is no public constructor taking a code, and that is deliberate rather than an oversight:
 * <strong>rows arrive from Flyway and from nowhere else.</strong> JPA needs the protected no-arg
 * constructor and nothing else needs one, so there is no Java path that can bring a row into
 * existence — which is the same guarantee {@code StatutoryCodificationRulesTest} states at the
 * service boundary, one layer down and by construction.
 */
@Entity
@Table(name = "aade_invoice_type")
class AadeInvoiceType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AADE's code, verbatim from the XSD. Never mutated, and there is no mutator. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_group", nullable = false, length = 30)
    private AadeInvoiceGroup invoiceGroup;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only, and — see the class comment — for nothing else. */
    protected AadeInvoiceType() {
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getDescription() {
        return description;
    }

    AadeInvoiceGroup getInvoiceGroup() {
        return invoiceGroup;
    }

    boolean isActive() {
        return active;
    }

    /** The one editable field. A description is a label; the code is the identity. */
    void describe(String newDescription) {
        this.description = newDescription;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
