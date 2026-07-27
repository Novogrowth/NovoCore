package gr.novotrade.novocore.core.tax;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.tax.InvalidVatClassException;
import gr.novotrade.novocore.core.api.tax.NewVatClass;
import gr.novotrade.novocore.core.api.tax.VatClassNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class VatClassServiceImpl implements VatClassService {

    private static final String ENTITY_TYPE = "VatClass";
    private static final BigDecimal MAX_RATE_PERCENT = new BigDecimal("100");

    private final VatClassRepository repository;
    private final AuditLogService auditLog;

    VatClassServiceImpl(VatClassRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VatClassView> all() {
        return toViews(repository.findAllByOrderByRatePercentAscCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VatClassView> active() {
        return toViews(repository.findByActiveTrueOrderByRatePercentAscCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VatClassView> find(long id) {
        return repository.findById(id).map(VatClassServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public VatClassView require(long id) {
        return find(id).orElseThrow(() -> new VatClassNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VatClassView> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return repository.findByCode(code.trim()).map(VatClassServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public VatClassView requireByCode(String code) {
        return findByCode(code).orElseThrow(() -> new VatClassNotFoundException(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VatClassView> reducedCounterparts() {
        return repository.findWithAReducedCounterpart().stream()
                .map(VatClass::getReducedCounterpart)
                .map(VatClassServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional
    public VatClassView create(NewVatClass request) {
        Objects.requireNonNull(request, "request");
        String code = requireText(request.code(), "VAT class code");
        String description = requireText(request.description(), "VAT class description");
        BigDecimal rate = request.ratePercent();

        if (rate.signum() < 0 || rate.compareTo(MAX_RATE_PERCENT) > 0) {
            throw new InvalidVatClassException(
                    "Rate " + rate.toPlainString() + " is not a percentage between 0 and 100. A "
                            + "rate given as a fraction (0.24 for 24%) would undercharge by a "
                            + "factor of 100.");
        }
        if (rate.scale() > VatClassView.RATE_SCALE) {
            throw new InvalidVatClassException(
                    "Rate " + rate.toPlainString() + " has more than "
                            + VatClassView.RATE_SCALE + " decimal places.");
        }
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new InvalidVatClassException(
                    "A VAT class with code '" + code + "' already exists. Codes come from the "
                            + "invoicing system and identify a class uniquely — two classes may "
                            + "share a rate, but never a code.");
        }

        VatClass saved = repository.save(
                new VatClass(code, description, rate.setScale(VatClassView.RATE_SCALE)));

        auditLog.record("vat-class.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "code", code,
                "description", description,
                "ratePercent", saved.getRatePercent().toPlainString()));

        return toView(saved);
    }

    @Override
    @Transactional
    public VatClassView mapToReducedCounterpart(long vatClassId, long reducedCounterpartId) {
        VatClass vatClass = repository.findById(vatClassId)
                .orElseThrow(() -> new VatClassNotFoundException(vatClassId));
        VatClass counterpart = repository.findById(reducedCounterpartId)
                .orElseThrow(() -> new VatClassNotFoundException(reducedCounterpartId));

        if (vatClassId == reducedCounterpartId) {
            throw new InvalidVatClassException(
                    "A VAT class cannot be its own island-reduced counterpart.");
        }
        // Lower, not merely different. The mapping's whole meaning is "the reduced rate for this
        // one", so a counterpart at or above the mainland rate is a wiring mistake, and the
        // seeded pairs (24→17, 13→9, 6→4, 4→3) all satisfy it.
        if (counterpart.getRatePercent().compareTo(vatClass.getRatePercent()) >= 0) {
            throw new InvalidVatClassException(
                    "Reduced counterpart '" + counterpart.getCode() + "' is rated "
                            + counterpart.getRatePercent().toPlainString() + "%, which is not "
                            + "lower than '" + vatClass.getCode() + "' at "
                            + vatClass.getRatePercent().toPlainString() + "%.");
        }
        // One level, not a chain. A→B→C would make "the reduced rate" ambiguous, and nothing in
        // the regime has two tiers of reduction.
        if (counterpart.getReducedCounterpart() != null) {
            throw new InvalidVatClassException(
                    "'" + counterpart.getCode() + "' already has an island-reduced counterpart of "
                            + "its own. Reduced mappings are one level deep, not a chain.");
        }
        // One-to-one. Also enforced by a unique index, but named here rather than surfacing as an
        // integrity violation.
        Optional<VatClass> alreadyClaiming = repository.findClaiming(reducedCounterpartId);
        if (alreadyClaiming.isPresent()
                && !Objects.equals(alreadyClaiming.get().getId(), vatClassId)) {
            throw new InvalidVatClassException(
                    "'" + counterpart.getCode() + "' is already the reduced counterpart of '"
                            + alreadyClaiming.get().getCode() + "'.");
        }

        vatClass.mapToReducedCounterpart(counterpart);

        auditLog.record("vat-class.reduced-counterpart-set", ENTITY_TYPE,
                String.valueOf(vatClassId), Map.of(
                        "code", vatClass.getCode(),
                        "reducedCounterpart", counterpart.getCode()));

        return toView(vatClass);
    }

    @Override
    @Transactional
    public VatClassView clearReducedCounterpart(long vatClassId) {
        VatClass vatClass = repository.findById(vatClassId)
                .orElseThrow(() -> new VatClassNotFoundException(vatClassId));

        VatClass previous = vatClass.getReducedCounterpart();
        vatClass.clearReducedCounterpart();

        if (previous != null) {
            auditLog.record("vat-class.reduced-counterpart-cleared", ENTITY_TYPE,
                    String.valueOf(vatClassId), Map.of(
                            "code", vatClass.getCode(),
                            "was", previous.getCode()));
        }

        return toView(vatClass);
    }

    @Override
    @Transactional
    public VatClassView describe(long id, String description) {
        String newDescription = requireText(description, "VAT class description");
        VatClass vatClass = repository.findById(id)
                .orElseThrow(() -> new VatClassNotFoundException(id));

        String previous = vatClass.getDescription();
        vatClass.describe(newDescription);

        auditLog.record("vat-class.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("from", previous, "to", newDescription));

        return toView(vatClass);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        VatClass vatClass = repository.findById(id)
                .orElseThrow(() -> new VatClassNotFoundException(id));
        if (!vatClass.isActive()) {
            return;
        }

        vatClass.setActive(false);
        auditLog.record("vat-class.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", vatClass.getCode()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        VatClass vatClass = repository.findById(id)
                .orElseThrow(() -> new VatClassNotFoundException(id));
        if (vatClass.isActive()) {
            return;
        }

        vatClass.setActive(true);
        auditLog.record("vat-class.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", vatClass.getCode()));
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidVatClassException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static List<VatClassView> toViews(List<VatClass> classes) {
        return classes.stream().map(VatClassServiceImpl::toView).toList();
    }

    private static VatClassView toView(VatClass vatClass) {
        VatClass counterpart = vatClass.getReducedCounterpart();
        return new VatClassView(
                vatClass.getId(),
                vatClass.getCode(),
                vatClass.getDescription(),
                vatClass.getRatePercent(),
                counterpart == null ? null : counterpart.getId(),
                vatClass.isActive());
    }
}
