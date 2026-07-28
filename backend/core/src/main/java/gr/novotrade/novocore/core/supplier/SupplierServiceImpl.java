package gr.novotrade.novocore.core.supplier;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.supplier.InvalidSupplierException;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierNotFoundException;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The VAT exemption reason is validated through {@link VatExemptionReasonService} rather than by
 * reaching into {@code ..core.tax}, which is the only route available: that slice's entities are
 * package-private. ADR 0003's boundary holds between slices of the core, not only at its edge.
 */
@Service
class SupplierServiceImpl implements SupplierService {

    private static final String ENTITY_TYPE = "Supplier";

    private final SupplierRepository repository;
    private final VatExemptionReasonService exemptionReasons;
    private final AuditLogService auditLog;

    SupplierServiceImpl(SupplierRepository repository,
            VatExemptionReasonService exemptionReasons, AuditLogService auditLog) {
        this.repository = repository;
        this.exemptionReasons = exemptionReasons;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierView> all() {
        return toViews(repository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierView> active() {
        return toViews(repository.findByActiveTrueOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierView> find(long id) {
        return repository.findById(id).map(SupplierServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierView require(long id) {
        return find(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierView> findByVatNumber(String vatNumber) {
        String normalised = optionalText(vatNumber);
        if (normalised == null) {
            // A blank VAT number matches nothing rather than everything. Returning the first
            // supplier without one would be an automatic match on the absence of the identifier
            // that makes automatic matching safe.
            return Optional.empty();
        }
        return repository.findByVatNumberIgnoreCase(normalised).map(SupplierServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierView> suggestMatches(String nameFragment, String email, String phone) {
        String name = optionalText(nameFragment);
        String mail = optionalText(email);
        String tel = optionalText(phone);
        if (name == null && mail == null && tel == null) {
            return List.of();
        }

        // One query per supplied criterion, merged here; an absent criterion contributes nothing
        // rather than matching everything.
        Map<Long, Supplier> candidates = new LinkedHashMap<>();
        if (name != null) {
            index(candidates, repository.findByNameContainingIgnoreCaseOrderByNameAsc(name));
        }
        if (mail != null) {
            index(candidates, repository.findByEmailIgnoreCaseOrderByNameAsc(mail));
        }
        if (tel != null) {
            index(candidates, repository.findByPhoneOrderByNameAsc(tel));
        }

        return candidates.values().stream()
                .sorted(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER))
                .map(SupplierServiceImpl::toView)
                .toList();
    }

    /** Keyed by id, so a supplier matching on two criteria is offered once, not twice. */
    private static void index(Map<Long, Supplier> candidates, List<Supplier> found) {
        found.forEach(supplier -> candidates.putIfAbsent(supplier.getId(), supplier));
    }

    @Override
    @Transactional
    public SupplierView create(NewSupplier request) {
        Objects.requireNonNull(request, "request");
        String name = requireText(request.name(), "Supplier name");
        String vatNumber = optionalText(request.vatNumber());

        if (repository.existsByNameIgnoreCase(name)) {
            throw new InvalidSupplierException(
                    "A supplier named '" + name + "' already exists.");
        }
        if (vatNumber != null && repository.existsByVatNumberIgnoreCase(vatNumber)) {
            throw new InvalidSupplierException(
                    "A supplier with VAT number '" + vatNumber + "' already exists. The VAT "
                            + "number is the authoritative identifier, so two suppliers cannot "
                            + "share one.");
        }
        requireCoherentVatStatus(name, request.vatStatus(), vatNumber,
                request.vatExemptionReasonId());

        Supplier saved = repository.save(new Supplier(
                name,
                optionalText(request.email()),
                optionalText(request.phone()),
                vatNumber,
                request.vatStatus(),
                request.vatExemptionReasonId()));

        auditLog.record("supplier.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "name", name,
                "vatStatus", request.vatStatus().name(),
                "vatNumber", vatNumber == null ? "(none)" : vatNumber));

        return toView(saved);
    }

    @Override
    @Transactional
    public SupplierView rename(long id, String newName) {
        String name = requireText(newName, "Supplier name");
        Supplier supplier = load(id);

        if (!supplier.getName().equalsIgnoreCase(name)
                && repository.existsByNameIgnoreCase(name)) {
            throw new InvalidSupplierException(
                    "A supplier named '" + name + "' already exists.");
        }

        String previous = supplier.getName();
        supplier.rename(name);

        auditLog.record("supplier.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("from", previous, "to", name));

        return toView(supplier);
    }

    @Override
    @Transactional
    public SupplierView changeContactDetails(long id, String email, String phone) {
        Supplier supplier = load(id);
        supplier.changeContactDetails(optionalText(email), optionalText(phone));

        auditLog.record("supplier.contact-details-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", supplier.getName()));

        return toView(supplier);
    }

    @Override
    @Transactional
    public SupplierView changeVatNumber(long id, String vatNumber) {
        Supplier supplier = load(id);
        String normalised = optionalText(vatNumber);

        if (normalised != null
                && !normalised.equalsIgnoreCase(nullSafe(supplier.getVatNumber()))
                && repository.existsByVatNumberIgnoreCase(normalised)) {
            throw new InvalidSupplierException(
                    "Another supplier already has VAT number '" + normalised + "'.");
        }
        // Clearing the number has to be checked against the status: an INTRA_EU_B2B supplier with
        // no VAT number is a state the database refuses, and failing here names the reason.
        requireCoherentVatStatus(supplier.getName(), supplier.getVatStatus(), normalised,
                supplier.getVatExemptionReasonId());

        supplier.changeVatNumber(normalised);

        auditLog.record("supplier.vat-number-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", supplier.getName(),
                "vatNumber", normalised == null ? "(none)" : normalised));

        return toView(supplier);
    }

    @Override
    @Transactional
    public SupplierView changeVatStatus(long id, VatStatus vatStatus, Long vatExemptionReasonId) {
        Objects.requireNonNull(vatStatus, "vatStatus");
        Supplier supplier = load(id);

        requireCoherentVatStatus(supplier.getName(), vatStatus, supplier.getVatNumber(),
                vatExemptionReasonId);

        VatStatus previous = supplier.getVatStatus();
        supplier.changeVatStatus(vatStatus, vatExemptionReasonId);

        auditLog.record("supplier.vat-status-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", supplier.getName(),
                "from", previous.name(),
                "to", vatStatus.name()));

        return toView(supplier);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        Supplier supplier = load(id);
        if (!supplier.isActive()) {
            return;
        }
        supplier.setActive(false);
        auditLog.record("supplier.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", supplier.getName()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        Supplier supplier = load(id);
        if (supplier.isActive()) {
            return;
        }
        supplier.setActive(true);
        auditLog.record("supplier.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", supplier.getName()));
    }

    private Supplier load(long id) {
        return repository.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    /**
     * The two rules that make a VAT status true of a party rather than merely recorded against it.
     *
     * <p>Both are also database CHECK constraints. Checked here as well so the failure names what
     * is wrong and why, instead of surfacing as a constraint violation on flush.
     */
    private void requireCoherentVatStatus(
            String name, VatStatus status, String vatNumber, Long exemptionReasonId) {
        if (status.requiresVatNumber() && vatNumber == null) {
            throw new InvalidSupplierException(
                    "Supplier '" + name + "' cannot be " + status + " without a VAT number: with "
                            + "no counterparty VAT number there is no reverse charge to apply.");
        }
        if (status.requiresExemptionReason() && exemptionReasonId == null) {
            throw new InvalidSupplierException(
                    "Supplier '" + name + "' cannot be " + status + " without a VAT exemption "
                            + "reason naming the article it is exempt under.");
        }
        if (exemptionReasonId != null && exemptionReasons.find(exemptionReasonId).isEmpty()) {
            throw new InvalidSupplierException(
                    "No VAT exemption reason with id " + exemptionReasonId + ".");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidSupplierException(what + " must not be blank.");
        }
        return value.trim();
    }

    /** Null and blank both mean "not known", normalised to null so there is one representation. */
    private static String optionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static List<SupplierView> toViews(List<Supplier> suppliers) {
        return suppliers.stream().map(SupplierServiceImpl::toView).toList();
    }

    private static SupplierView toView(Supplier supplier) {
        return new SupplierView(
                supplier.getId(),
                supplier.getName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getVatNumber(),
                supplier.getVatStatus(),
                supplier.getVatExemptionReasonId(),
                supplier.isActive());
    }
}
