package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.sales.InvalidPaymentMethodException;
import gr.novotrade.novocore.core.api.sales.PaymentMethodNotFoundException;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentMethodServiceImpl implements PaymentMethodService {

    private static final String ENTITY_TYPE = "PaymentMethod";

    private final PaymentMethodRepository repository;
    private final AuditLogService auditLog;

    PaymentMethodServiceImpl(PaymentMethodRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodView> all() {
        return toViews(repository.findAllByOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodView> active() {
        return toViews(repository.findByActiveTrueOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethodView> find(SettlementMethod method) {
        return repository.findById(method).map(PaymentMethodServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodView require(SettlementMethod method) {
        return find(method).orElseThrow(() -> new PaymentMethodNotFoundException(method));
    }

    @Override
    @Transactional
    public PaymentMethodView describe(SettlementMethod method, String description) {
        PaymentMethod row = load(method);
        if (description == null || description.isBlank()) {
            throw new InvalidPaymentMethodException("A description must not be blank.");
        }
        String corrected = description.trim();
        if (corrected.equals(row.getDescription())) {
            return toView(row);
        }

        String previous = row.getDescription();
        row.describe(corrected);
        auditLog.record("payment-method.described", ENTITY_TYPE, method.name(),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(row);
    }

    @Override
    @Transactional
    public PaymentMethodView changeSortCode(SettlementMethod method, int sortCode) {
        PaymentMethod row = load(method);
        if (row.getSortCode() == sortCode) {
            return toView(row);
        }
        if (repository.existsBySortCode(sortCode)) {
            throw new InvalidPaymentMethodException(
                    "Sort code " + sortCode + " is already used by another payment method. Sort "
                            + "codes are unique so the list has one definite order.");
        }

        int previous = row.getSortCode();
        row.changeSortCode(sortCode);
        auditLog.record("payment-method.sort-code-changed", ENTITY_TYPE, method.name(),
                Map.of("previousSortCode", String.valueOf(previous),
                        "sortCode", String.valueOf(sortCode)));
        return toView(row);
    }

    /**
     * ⚠️ Deliberately unconditional, and the reason is the same one the document-type rule states:
     * <strong>deactivating never breaks documents already settled by this method.</strong> Invoices
     * that used it keep pointing at it and stay explicable. What changes is that
     * {@code SalesInvoiceServiceImpl} refuses to record a NEW invoice settled by it — setting is
     * refused, holding is not.
     */
    @Override
    @Transactional
    public void deactivate(SettlementMethod method) {
        PaymentMethod row = load(method);
        if (!row.isActive()) {
            return;
        }
        row.setActive(false);
        auditLog.record("payment-method.deactivated", ENTITY_TYPE, method.name(),
                Map.of("description", row.getDescription()));
    }

    @Override
    @Transactional
    public void reactivate(SettlementMethod method) {
        PaymentMethod row = load(method);
        if (row.isActive()) {
            return;
        }
        row.setActive(true);
        auditLog.record("payment-method.reactivated", ENTITY_TYPE, method.name(),
                Map.of("description", row.getDescription()));
    }

    private PaymentMethod load(SettlementMethod method) {
        return repository.findById(method)
                .orElseThrow(() -> new PaymentMethodNotFoundException(method));
    }

    private static List<PaymentMethodView> toViews(List<PaymentMethod> rows) {
        return rows.stream().map(PaymentMethodServiceImpl::toView).toList();
    }

    /**
     * ⚠️ <strong>The behaviour fields are read from the ENUM, never from the row.</strong>
     *
     * <p>The myDATA payment code in particular: the brief for this table asked for it as a column,
     * and it is not one, because the codes have been on {@link SettlementMethod} since it was
     * written. Storing them would create a second record of one thing — which is exactly what
     * {@code PaymentMethodIT}'s drift test exists to prevent, and would then need a drift test of
     * its own. Resolving them here means there is nothing that *can* disagree.
     */
    private static PaymentMethodView toView(PaymentMethod row) {
        SettlementMethod method = row.getMethod();
        return new PaymentMethodView(
                method,
                row.getAbbreviation(),
                row.getDescription(),
                method.mydataPaymentCode().orElse(null),
                method.settlesImmediately(),
                method.subjectToCashLimit(),
                row.getSortCode(),
                row.isActive());
    }
}
