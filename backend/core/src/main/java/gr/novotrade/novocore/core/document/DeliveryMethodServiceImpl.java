package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.document.DeliveryMethodNotFoundException;
import gr.novotrade.novocore.core.api.document.DeliveryMethodService;
import gr.novotrade.novocore.core.api.document.DeliveryMethodView;
import gr.novotrade.novocore.core.api.document.InvalidDeliveryMethodException;
import gr.novotrade.novocore.core.api.document.NewDeliveryMethod;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryMethodServiceImpl implements DeliveryMethodService {

    private static final String ENTITY_TYPE = "DeliveryMethod";

    private final DeliveryMethodRepository repository;
    private final AuditLogService auditLog;

    DeliveryMethodServiceImpl(DeliveryMethodRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryMethodView> all() {
        return toViews(repository.findAllByOrderByAbbreviationAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryMethodView> active() {
        return toViews(repository.findByActiveTrueOrderByAbbreviationAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryMethodView> find(long id) {
        return repository.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryMethodView require(long id) {
        return find(id).orElseThrow(() -> new DeliveryMethodNotFoundException(id));
    }

    @Override
    @Transactional
    public DeliveryMethodView create(NewDeliveryMethod request) {
        String abbreviation = request.abbreviation().trim();
        if (repository.existsByAbbreviationIgnoreCase(abbreviation)) {
            throw new InvalidDeliveryMethodException(
                    "A delivery method abbreviated \"" + abbreviation + "\" already exists.");
        }

        DeliveryMethod saved = repository.save(
                new DeliveryMethod(abbreviation, request.description().trim()));

        auditLog.record("delivery-method.created", ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of("abbreviation", abbreviation, "description", saved.getDescription()));

        return toView(saved);
    }

    @Override
    @Transactional
    public DeliveryMethodView describe(long id, String description) {
        DeliveryMethod method = repository.findById(id)
                .orElseThrow(() -> new DeliveryMethodNotFoundException(id));
        if (description == null || description.isBlank()) {
            throw new InvalidDeliveryMethodException("A description must not be blank.");
        }
        String corrected = description.trim();
        if (corrected.equals(method.getDescription())) {
            return toView(method);
        }

        String previous = method.getDescription();
        method.describe(corrected);
        auditLog.record("delivery-method.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(method);
    }

    @Override
    @Transactional
    public DeliveryMethodView changeAbbreviation(long id, String abbreviation) {
        DeliveryMethod method = repository.findById(id)
                .orElseThrow(() -> new DeliveryMethodNotFoundException(id));
        if (abbreviation == null || abbreviation.isBlank()) {
            throw new InvalidDeliveryMethodException("An abbreviation must not be blank.");
        }
        String corrected = abbreviation.trim();
        if (corrected.equals(method.getAbbreviation())) {
            return toView(method);
        }
        if (isNamedByARecordedDocument(id)) {
            throw new InvalidDeliveryMethodException(
                    "The abbreviation of delivery method \"" + method.getAbbreviation()
                            + "\" cannot be changed, because documents already name it.");
        }
        if (repository.existsByAbbreviationIgnoreCase(corrected)) {
            throw new InvalidDeliveryMethodException(
                    "A delivery method abbreviated \"" + corrected + "\" already exists.");
        }

        String previous = method.getAbbreviation();
        method.changeAbbreviation(corrected);
        auditLog.record("delivery-method.abbreviation-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousAbbreviation", previous, "abbreviation", corrected));
        return toView(method);
    }

    /**
     * ⚠️⚠️ <strong>Always false, and more strongly than on the purchase series: measured
     * 2026-08-04, NO table in this schema has a foreign key to {@code delivery_method} at all.</strong>
     * It is a list the business maintains ahead of the document that will use it (18b, dispatch).
     *
     * <p>Written in the same shape as the sales series' real predicate so whoever wires a delivery
     * method onto a document replaces a method body rather than discovering a missing guard.
     * {@code DocumentReferenceGraphIT} pins the emptiness and goes red the day it stops holding.
     */
    private boolean isNamedByARecordedDocument(long deliveryMethodId) {
        return false;
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        DeliveryMethod method = repository.findById(id)
                .orElseThrow(() -> new DeliveryMethodNotFoundException(id));
        if (!method.isActive()) {
            return;
        }
        method.setActive(false);
        auditLog.record("delivery-method.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", method.getAbbreviation()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        DeliveryMethod method = repository.findById(id)
                .orElseThrow(() -> new DeliveryMethodNotFoundException(id));
        if (method.isActive()) {
            return;
        }
        method.setActive(true);
        auditLog.record("delivery-method.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", method.getAbbreviation()));
    }

    private List<DeliveryMethodView> toViews(List<DeliveryMethod> methods) {
        return methods.stream().map(this::toView).toList();
    }

    private DeliveryMethodView toView(DeliveryMethod method) {
        return new DeliveryMethodView(
                method.getId(),
                method.getAbbreviation(),
                method.getDescription(),
                isNamedByARecordedDocument(method.getId()),
                method.isActive());
    }
}
