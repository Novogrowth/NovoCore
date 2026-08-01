package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.product.InvalidUnitOfMeasureException;
import gr.novotrade.novocore.core.api.product.NewUnitOfMeasure;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureNotFoundException;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureView;
import gr.novotrade.novocore.core.support.Specifications;
import gr.novotrade.novocore.core.support.TextSearch;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UnitOfMeasureServiceImpl implements UnitOfMeasureService {

    /**
     * Row 7 of the search target list — "Name / Code". Spelled as entity properties.
     *
     * <p>⚠️ The label column here is {@code name}; on {@code vat_class} the same row of that table
     * is called {@code description}. They are not interchangeable and the PATCH routes differ
     * accordingly.
     */
    private static final String[] SEARCHABLE = {"code", "name"};

    private static final String ENTITY_TYPE = "UnitOfMeasure";

    private final UnitOfMeasureRepository repository;
    private final ProductRepository products;
    private final AuditLogService auditLog;

    UnitOfMeasureServiceImpl(UnitOfMeasureRepository repository, ProductRepository products,
            AuditLogService auditLog) {
        this.repository = repository;
        this.products = products;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOfMeasureView> all() {
        return toViews(repository.findAllByOrderByCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOfMeasureView> active() {
        return toViews(repository.findByActiveTrueOrderByCodeAsc());
    }

    /**
     * Row 7 of the search target list: code and name. The myDATA code is not searched — see the
     * interface, and V30 for why it has no index either.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UnitOfMeasureView> search(String term, boolean activeOnly) {
        return toViews(repository.findAll(
                Specifications.<UnitOfMeasure>activeOnly(activeOnly)
                        .and(TextSearch.matching(term, SEARCHABLE)),
                Sort.by(Sort.Order.asc("code"))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitOfMeasureView> find(long id) {
        return repository.findById(id).map(UnitOfMeasureServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitOfMeasureView require(long id) {
        return find(id).orElseThrow(() -> new UnitOfMeasureNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitOfMeasureView> findByCode(String code) {
        String normalised = optionalText(code);
        if (normalised == null) {
            return Optional.empty();
        }
        return repository.findByCodeIgnoreCase(normalised).map(UnitOfMeasureServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitOfMeasureView requireByCode(String code) {
        return findByCode(code)
                .orElseThrow(() -> UnitOfMeasureNotFoundException.forCode(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOfMeasureView> withoutMydataCode() {
        return toViews(repository.findByMydataCodeIsNullOrderByCodeAsc());
    }

    @Override
    @Transactional
    public UnitOfMeasureView create(NewUnitOfMeasure request) {
        Objects.requireNonNull(request, "request");
        String code = requireText(request.code(), "Unit code");
        String name = requireText(request.name(), "Unit name");
        String mydataCode = optionalText(request.mydataCode());

        if (repository.existsByCodeIgnoreCase(code)) {
            throw new InvalidUnitOfMeasureException(
                    "A unit of measure with code '" + code + "' already exists.");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new InvalidUnitOfMeasureException(
                    "A unit of measure named '" + name + "' already exists.");
        }
        requireUnusedMydataCode(mydataCode);

        UnitOfMeasure saved = repository.save(new UnitOfMeasure(
                code, name, request.fractionalQuantityAllowed(), mydataCode));

        auditLog.record("unit-of-measure.created", ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of(
                        "code", code,
                        "name", name,
                        "fractionalQuantityAllowed",
                        String.valueOf(request.fractionalQuantityAllowed()),
                        "mydataCode", mydataCode == null ? "(none)" : mydataCode));

        return toView(saved);
    }

    @Override
    @Transactional
    public UnitOfMeasureView rename(long id, String newName) {
        String name = requireText(newName, "Unit name");
        UnitOfMeasure unit = load(id);

        if (!unit.getName().equalsIgnoreCase(name) && repository.existsByNameIgnoreCase(name)) {
            throw new InvalidUnitOfMeasureException(
                    "A unit of measure named '" + name + "' already exists.");
        }

        String previous = unit.getName();
        unit.rename(name);

        auditLog.record("unit-of-measure.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", unit.getCode(), "from", previous, "to", name));

        return toView(unit);
    }

    @Override
    @Transactional
    public UnitOfMeasureView recordMydataCode(long id, String mydataCode) {
        String code = requireText(mydataCode, "myDATA unit code");
        UnitOfMeasure unit = load(id);

        // Once, not repeatedly. A myDATA code that has been transmitted describes documents already
        // filed under it, so changing it would silently re-describe them. Deactivate and replace
        // the unit instead, which leaves both states visible.
        if (unit.getMydataCode() != null) {
            throw new InvalidUnitOfMeasureException(
                    "Unit '" + unit.getCode() + "' already has myDATA code '"
                            + unit.getMydataCode() + "'. It is not changeable: documents may "
                            + "already have been transmitted under it. Deactivate this unit and "
                            + "create a replacement if the mapping was wrong.");
        }
        requireUnusedMydataCode(code);

        unit.recordMydataCode(code);

        auditLog.record("unit-of-measure.mydata-code-recorded", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", unit.getCode(), "mydataCode", code));

        return toView(unit);
    }

    @Override
    @Transactional
    public UnitOfMeasureView changeFractionalQuantityAllowed(long id, boolean allowed) {
        UnitOfMeasure unit = load(id);
        unit.changeFractionalQuantityAllowed(allowed);

        auditLog.record("unit-of-measure.fractional-quantity-changed", ENTITY_TYPE,
                String.valueOf(id),
                Map.of("code", unit.getCode(), "allowed", String.valueOf(allowed)));

        return toView(unit);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        UnitOfMeasure unit = load(id);
        if (!unit.isActive()) {
            return;
        }

        // Refused rather than cascaded. A product whose unit has been retired carries a quantity
        // that no longer states what it counts, and step 6's lots will inherit that quantity.
        long inUse = products.countByUnitOfMeasureId(id);
        if (inUse > 0) {
            throw new InvalidUnitOfMeasureException(
                    "Unit '" + unit.getCode() + "' is still used by " + inUse + " product(s), so "
                            + "deactivating it would leave their quantities stating nothing. Move "
                            + "those products to another unit first.");
        }

        unit.setActive(false);
        auditLog.record("unit-of-measure.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", unit.getCode()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        UnitOfMeasure unit = load(id);
        if (unit.isActive()) {
            return;
        }
        unit.setActive(true);
        auditLog.record("unit-of-measure.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("code", unit.getCode()));
    }

    private UnitOfMeasure load(long id) {
        return repository.findById(id).orElseThrow(() -> new UnitOfMeasureNotFoundException(id));
    }

    private void requireUnusedMydataCode(String mydataCode) {
        // Absence is not a collision: every unit currently has no mapping at all.
        if (mydataCode != null && repository.existsByMydataCode(mydataCode)) {
            throw new InvalidUnitOfMeasureException(
                    "Another unit of measure already has myDATA code '" + mydataCode + "'.");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidUnitOfMeasureException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static List<UnitOfMeasureView> toViews(List<UnitOfMeasure> units) {
        return units.stream().map(UnitOfMeasureServiceImpl::toView).toList();
    }

    static UnitOfMeasureView toView(UnitOfMeasure unit) {
        return new UnitOfMeasureView(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.isFractionalQuantityAllowed(),
                unit.getMydataCode(),
                unit.isActive());
    }
}
