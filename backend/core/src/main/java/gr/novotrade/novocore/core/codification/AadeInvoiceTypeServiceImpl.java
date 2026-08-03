package gr.novotrade.novocore.core.codification;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceGroup;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeNotFoundException;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeView;
import gr.novotrade.novocore.core.api.codification.InvalidAadeInvoiceTypeException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AadeInvoiceTypeServiceImpl implements AadeInvoiceTypeService {

    private static final String ENTITY_TYPE = "AadeInvoiceType";

    private static final List<AadeInvoiceGroup> ISSUED_GROUPS =
            List.of(AadeInvoiceGroup.ISSUER_MATCHED, AadeInvoiceGroup.ISSUER_UNMATCHED);
    private static final List<AadeInvoiceGroup> RECEIVED_GROUPS =
            List.of(AadeInvoiceGroup.RECIPIENT_MATCHED, AadeInvoiceGroup.RECIPIENT_UNMATCHED);

    private final AadeInvoiceTypeRepository repository;
    private final AuditLogService auditLog;

    AadeInvoiceTypeServiceImpl(AadeInvoiceTypeRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadeInvoiceTypeView> all() {
        return toViews(repository.findAllByOrderByIdAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadeInvoiceTypeView> active() {
        return toViews(repository.findByActiveTrueOrderByIdAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AadeInvoiceTypeView> find(long id) {
        return repository.findById(id).map(AadeInvoiceTypeServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public AadeInvoiceTypeView require(long id) {
        return find(id).orElseThrow(() -> new AadeInvoiceTypeNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AadeInvoiceTypeView> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByCodeIgnoreCase(code.trim())
                .map(AadeInvoiceTypeServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public AadeInvoiceTypeView requireByCode(String code) {
        return findByCode(code)
                .orElseThrow(() -> AadeInvoiceTypeNotFoundException.forCode(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadeInvoiceTypeView> inGroup(AadeInvoiceGroup group) {
        return toViews(repository.findByInvoiceGroupOrderByIdAsc(group));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadeInvoiceTypeView> issued() {
        return toViews(repository.findByInvoiceGroupInOrderByIdAsc(ISSUED_GROUPS));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AadeInvoiceTypeView> received() {
        return toViews(repository.findByInvoiceGroupInOrderByIdAsc(RECEIVED_GROUPS));
    }

    @Override
    @Transactional
    public AadeInvoiceTypeView describe(long id, String description) {
        AadeInvoiceType type = repository.findById(id)
                .orElseThrow(() -> new AadeInvoiceTypeNotFoundException(id));

        if (description == null || description.isBlank()) {
            throw new InvalidAadeInvoiceTypeException(
                    "A description must not be blank. AADE code " + type.getCode()
                            + " would then have no label at all, and there is no create path to "
                            + "restore it from — the seed is the only source.");
        }
        String corrected = description.trim();
        if (corrected.equals(type.getDescription())) {
            return toView(type);
        }

        String previous = type.getDescription();
        type.describe(corrected);

        // The previous value is recorded because this is the ONE editable field on a statutory
        // codification, and the seed is the only other record of what AADE actually published.
        auditLog.record("aade-invoice-type.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", type.getCode(),
                        "previousDescription", previous,
                        "description", corrected));

        return toView(type);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        AadeInvoiceType type = repository.findById(id)
                .orElseThrow(() -> new AadeInvoiceTypeNotFoundException(id));
        if (!type.isActive()) {
            return;
        }
        type.setActive(false);
        auditLog.record("aade-invoice-type.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", type.getCode()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        AadeInvoiceType type = repository.findById(id)
                .orElseThrow(() -> new AadeInvoiceTypeNotFoundException(id));
        if (type.isActive()) {
            return;
        }
        type.setActive(true);
        auditLog.record("aade-invoice-type.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", type.getCode()));
    }

    private static List<AadeInvoiceTypeView> toViews(List<AadeInvoiceType> types) {
        return types.stream().map(AadeInvoiceTypeServiceImpl::toView).toList();
    }

    private static AadeInvoiceTypeView toView(AadeInvoiceType type) {
        return new AadeInvoiceTypeView(
                type.getId(),
                type.getCode(),
                type.getDescription(),
                type.getInvoiceGroup(),
                type.isActive());
    }
}
