package gr.novotrade.novocore.core.codification;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodNotFoundException;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodView;
import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AadePaymentMethodServiceImpl implements AadePaymentMethodService {

    private static final String ENTITY_TYPE = "AadePaymentMethod";

    private final AadePaymentMethodRepository repository;
    private final AuditLogService auditLog;

    AadePaymentMethodServiceImpl(AadePaymentMethodRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadePaymentMethodView> all() {
        return toViews(repository.findAllByOrderByCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadePaymentMethodView> active() {
        return toViews(repository.findByActiveTrueOrderByCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AadePaymentMethodView> find(long id) {
        return repository.findById(id).map(AadePaymentMethodServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public AadePaymentMethodView require(long id) {
        return find(id).orElseThrow(() -> new AadePaymentMethodNotFoundException(id));
    }

    @Override
    @Transactional
    public AadePaymentMethodView describe(long id, String description) {
        AadePaymentMethod article = repository.findById(id)
                .orElseThrow(() -> new AadePaymentMethodNotFoundException(id));

        if (description == null || description.isBlank()) {
            throw new InvalidInputException(
                    "A description must not be blank. AADE payment code " + article.getCode()
                            + " would then have no label at all, and there is no create path to "
                            + "restore it from — the seed is the only source.");
        }
        String corrected = description.trim();
        if (corrected.equals(article.getDescription())) {
            return toView(article);
        }

        String previous = article.getDescription();
        article.describe(corrected);

        // Recorded because this is the ONE editable field on a statutory codification, and V37's
        // seed is the only other record of what AADE actually published.
        auditLog.record("aade-payment-method.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", String.valueOf(article.getCode()),
                        "previousDescription", previous,
                        "description", corrected));

        return toView(article);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        AadePaymentMethod article = repository.findById(id)
                .orElseThrow(() -> new AadePaymentMethodNotFoundException(id));
        if (!article.isActive()) {
            return;
        }
        article.setActive(false);
        auditLog.record("aade-payment-method.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", String.valueOf(article.getCode())));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        AadePaymentMethod article = repository.findById(id)
                .orElseThrow(() -> new AadePaymentMethodNotFoundException(id));
        if (article.isActive()) {
            return;
        }
        article.setActive(true);
        auditLog.record("aade-payment-method.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", String.valueOf(article.getCode())));
    }

    private static List<AadePaymentMethodView> toViews(List<AadePaymentMethod> articles) {
        return articles.stream().map(AadePaymentMethodServiceImpl::toView).toList();
    }

    private static AadePaymentMethodView toView(AadePaymentMethod article) {
        return new AadePaymentMethodView(
                article.getId(), article.getCode(), article.getDescription(), article.isActive());
    }
}
