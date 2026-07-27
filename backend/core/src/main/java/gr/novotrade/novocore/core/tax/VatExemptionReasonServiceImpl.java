package gr.novotrade.novocore.core.tax;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.tax.InvalidVatExemptionReasonException;
import gr.novotrade.novocore.core.api.tax.NewVatExemptionReason;
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
    public VatExemptionReasonView create(NewVatExemptionReason request) {
        String description = requireText(request.description(), "Description");
        String mydataCode = requireText(request.mydataCode(), "myDATA code");

        if (request.code() <= 0) {
            throw new InvalidVatExemptionReasonException(
                    "AADE exemption reason codes are positive integers; got " + request.code()
                            + ".");
        }
        if (repository.existsByCode(request.code())) {
            throw new InvalidVatExemptionReasonException(
                    "A VAT exemption reason with AADE code " + request.code()
                            + " already exists.");
        }
        // The myDATA string is what gets transmitted. Two reasons sharing one would mean an
        // exempt line could be reported under a reason nobody selected.
        if (repository.existsByMydataCode(mydataCode)) {
            throw new InvalidVatExemptionReasonException(
                    "A VAT exemption reason with myDATA code '" + mydataCode
                            + "' already exists.");
        }

        VatExemptionReason saved = repository.save(new VatExemptionReason(
                request.code(), description, mydataCode, request.inputVatDeductible()));

        auditLog.record("vat-exemption-reason.created", ENTITY_TYPE,
                String.valueOf(saved.getId()), Map.of(
                        "code", String.valueOf(request.code()),
                        "mydataCode", mydataCode,
                        "inputVatDeductible", String.valueOf(request.inputVatDeductible())));

        return toView(saved);
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
