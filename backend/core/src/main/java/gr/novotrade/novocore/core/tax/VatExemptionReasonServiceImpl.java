package gr.novotrade.novocore.core.tax;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.tax.InvalidVatExemptionReasonException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class VatExemptionReasonServiceImpl implements VatExemptionReasonService {

    private static final String ENTITY_TYPE = "VatExemptionReason";

    private final VatExemptionReasonRepository repository;
    private final AuditLogService auditLog;

    VatExemptionReasonServiceImpl(VatExemptionReasonRepository repository,
            AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VatExemptionReasonView> all() {
        return toViews(repository.findAllByOrderByCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VatExemptionReasonView> active() {
        return toViews(repository.findByActiveTrueOrderByCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VatExemptionReasonView> find(long id) {
        return repository.findById(id).map(VatExemptionReasonServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public VatExemptionReasonView require(long id) {
        return find(id).orElseThrow(() -> new VatExemptionReasonNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VatExemptionReasonView> findByCode(int code) {
        return repository.findByCode(code).map(VatExemptionReasonServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public VatExemptionReasonView requireByCode(int code) {
        return findByCode(code)
                .orElseThrow(() -> VatExemptionReasonNotFoundException.forCode(code));
    }

    @Override
    @Transactional
    public VatExemptionReasonView describe(long id, String description) {
        VatExemptionReason reason = repository.findById(id)
                .orElseThrow(() -> new VatExemptionReasonNotFoundException(id));
        String corrected = requireText(description, "Description");

        if (corrected.equals(reason.getDescription())) {
            return toView(reason);
        }

        String previous = reason.getDescription();
        reason.describe(corrected);

        // The previous value is recorded because this is the ONE editable field on a statutory
        // codification, and "who changed the label on AADE code 6, and from what" is the question
        // somebody asks when a picker stops reading the way they remember.
        auditLog.record("vat-exemption-reason.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", String.valueOf(reason.getCode()),
                        "previousDescription", previous,
                        "description", corrected));

        return toView(reason);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        VatExemptionReason reason = repository.findById(id)
                .orElseThrow(() -> new VatExemptionReasonNotFoundException(id));
        if (!reason.isActive()) {
            return;
        }

        reason.setActive(false);
        auditLog.record("vat-exemption-reason.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", String.valueOf(reason.getCode())));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        VatExemptionReason reason = repository.findById(id)
                .orElseThrow(() -> new VatExemptionReasonNotFoundException(id));
        if (reason.isActive()) {
            return;
        }

        reason.setActive(true);
        auditLog.record("vat-exemption-reason.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", String.valueOf(reason.getCode())));
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidVatExemptionReasonException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static List<VatExemptionReasonView> toViews(List<VatExemptionReason> reasons) {
        return reasons.stream().map(VatExemptionReasonServiceImpl::toView).toList();
    }

    private static VatExemptionReasonView toView(VatExemptionReason reason) {
        return new VatExemptionReasonView(
                reason.getId(),
                reason.getCode(),
                reason.getDescription(),
                reason.getMydataCode(),
                reason.isInputVatDeductible(),
                reason.isActive());
    }
}
