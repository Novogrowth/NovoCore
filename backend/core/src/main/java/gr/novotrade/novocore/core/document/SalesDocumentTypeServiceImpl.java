package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeView;
import gr.novotrade.novocore.core.api.document.DocumentTypeNotFoundException;
import gr.novotrade.novocore.core.api.document.InvalidDocumentTypeException;
import gr.novotrade.novocore.core.api.document.NewSalesDocumentType;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeService;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SalesDocumentTypeServiceImpl implements SalesDocumentTypeService {

    private static final String ENTITY_TYPE = "SalesDocumentType";

    private final SalesDocumentTypeRepository repository;
    private final AadeInvoiceTypeService aadeInvoiceTypes;
    private final AuditLogService auditLog;

    SalesDocumentTypeServiceImpl(SalesDocumentTypeRepository repository,
            AadeInvoiceTypeService aadeInvoiceTypes, AuditLogService auditLog) {
        this.repository = repository;
        this.aadeInvoiceTypes = aadeInvoiceTypes;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesDocumentTypeView> all() {
        return toViews(repository.findAllByOrderByDescriptionAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesDocumentTypeView> active() {
        return toViews(repository.findByActiveTrueOrderByDescriptionAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesDocumentTypeView> drafts() {
        return toViews(repository
                .findByAffectsStockIsNullOrTransfersStockIsNullOrderByDescriptionAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesDocumentTypeView> find(long id) {
        return repository.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesDocumentTypeView require(long id) {
        return find(id).orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
    }

    @Override
    @Transactional
    public SalesDocumentTypeView create(NewSalesDocumentType request) {
        String description = request.description().trim();
        requireDescriptionIsFree(description, null);
        requireCompatibleStockBehaviour(request.affectsStock(), request.transfersStock());
        requireIssuerSide(request.aadeInvoiceTypeId());

        // A type whose stock behaviour is undecided is created INACTIVE — a draft. Refusing
        // instead would make it impossible to save a type before the stock question is answered,
        // and defaulting a flag to false would record a decision nobody took. The database CHECK
        // says the same thing from the other side and cannot be bypassed by a second write path.
        boolean decided = request.affectsStock() != null && request.transfersStock() != null;

        SalesDocumentType saved = repository.save(new SalesDocumentType(
                description,
                request.affectsStock(),
                request.transfersStock(),
                request.requiresMydataTransmission(),
                request.aadeInvoiceTypeId(),
                decided));

        auditLog.record("sales-document-type.created", ENTITY_TYPE,
                String.valueOf(saved.getId()), Map.of(
                        "description", description,
                        "affectsStock", String.valueOf(request.affectsStock()),
                        "transfersStock", String.valueOf(request.transfersStock()),
                        "requiresMydataTransmission",
                        String.valueOf(request.requiresMydataTransmission()),
                        // "(none)" rather than omitted, so the log distinguishes a type created
                        // without an AADE mapping — six of the nineteen — from one created before
                        // this detail was captured. Map.of rejects a null value in any case.
                        "aadeInvoiceTypeId", request.aadeInvoiceTypeId() == null
                                ? "(none)" : String.valueOf(request.aadeInvoiceTypeId()),
                        "active", String.valueOf(decided)));

        return toView(saved);
    }

    @Override
    @Transactional
    public SalesDocumentTypeView describe(long id, String description) {
        SalesDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
        if (description == null || description.isBlank()) {
            throw new InvalidDocumentTypeException("A description must not be blank.");
        }
        String corrected = description.trim();
        if (corrected.equals(type.getDescription())) {
            return toView(type);
        }
        requireDescriptionIsFree(corrected, id);

        String previous = type.getDescription();
        type.describe(corrected);
        auditLog.record("sales-document-type.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(type);
    }

    @Override
    @Transactional
    public SalesDocumentTypeView changeStockBehaviour(
            long id, boolean affectsStock, boolean transfersStock) {
        SalesDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
        requireCompatibleStockBehaviour(affectsStock, transfersStock);

        type.changeStockBehaviour(affectsStock, transfersStock);
        auditLog.record("sales-document-type.stock-behaviour-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of(
                        "affectsStock", String.valueOf(affectsStock),
                        "transfersStock", String.valueOf(transfersStock)));
        return toView(type);
    }

    @Override
    @Transactional
    public SalesDocumentTypeView changeMydataTransmissionRequired(long id, boolean required) {
        SalesDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
        type.changeMydataTransmissionRequired(required);
        auditLog.record("sales-document-type.mydata-transmission-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of("requiresMydataTransmission", String.valueOf(required)));
        return toView(type);
    }

    @Override
    @Transactional
    public SalesDocumentTypeView mapToAadeInvoiceType(long id, Long aadeInvoiceTypeId) {
        SalesDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
        requireIssuerSide(aadeInvoiceTypeId);

        type.mapToAadeInvoiceType(aadeInvoiceTypeId);
        auditLog.record("sales-document-type.aade-type-mapped", ENTITY_TYPE, String.valueOf(id),
                Map.of("aadeInvoiceTypeId",
                        aadeInvoiceTypeId == null ? "(none)" : String.valueOf(aadeInvoiceTypeId)));
        return toView(type);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        SalesDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
        if (!type.isActive()) {
            return;
        }
        // Deliberately unconditional. Documents already recorded under this type keep pointing at
        // it and stay explicable; deactivating only removes it from what a form offers.
        type.setActive(false);
        auditLog.record("sales-document-type.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("description", type.getDescription()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        SalesDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(id));
        if (type.isActive()) {
            return;
        }
        if (type.isDraft()) {
            throw new InvalidDocumentTypeException(
                    "\"" + type.getDescription() + "\" cannot be activated while its stock "
                            + "behaviour is undecided. Whether a document of this type moves "
                            + "stock decides whether recording one consumes inventory, and an "
                            + "unanswered question must not become a silent \"no\". Set both "
                            + "affectsStock and transfersStock first.");
        }
        type.setActive(true);
        auditLog.record("sales-document-type.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("description", type.getDescription()));
    }

    private void requireDescriptionIsFree(String description, Long selfId) {
        repository.findByDescriptionIgnoreCase(description).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new InvalidDocumentTypeException(
                        "A sales document type called \"" + description + "\" already exists.");
            }
        });
    }

    /**
     * A type that transfers stock necessarily affects it. The two are one decision, and the
     * incoherent combination would otherwise be storable and then activated.
     */
    private static void requireCompatibleStockBehaviour(Boolean affects, Boolean transfers) {
        if (Boolean.TRUE.equals(transfers) && Boolean.FALSE.equals(affects)) {
            throw new InvalidDocumentTypeException(
                    "A document type that transfers stock necessarily affects it. "
                            + "transfersStock=true with affectsStock=false is not a state a "
                            + "document can be in.");
        }
    }

    /**
     * ⚠️ A sales document type may only name an AADE code from the issuer side of annex 8.1.
     *
     * <p>The XSD has ONE enumeration covering both directions and the sales/purchase split is
     * Novocore's, read from annex 8.1's group headings — so nothing in AADE's artefacts stops a
     * sales type from naming "Ενοίκιο Έξοδο". This is where that is stopped.
     */
    private void requireIssuerSide(Long aadeInvoiceTypeId) {
        if (aadeInvoiceTypeId == null) {
            return;
        }
        AadeInvoiceTypeView aadeType = aadeInvoiceTypes.require(aadeInvoiceTypeId);
        if (!aadeType.group().hasCounterparty() || !aadeType.group().issuedByUs()) {
            throw new InvalidDocumentTypeException(
                    "AADE invoice type " + aadeType.code() + " (" + aadeType.description()
                            + ") is in group " + aadeType.group() + ", which is not a document "
                            + "this business issues. A sales document type may only name a code "
                            + "from the two issuer groups of annex 8.1.");
        }
    }

    private List<SalesDocumentTypeView> toViews(List<SalesDocumentType> types) {
        return types.stream().map(this::toView).toList();
    }

    private SalesDocumentTypeView toView(SalesDocumentType type) {
        // Resolved inside the transaction and materialised into the view, so no caller ever has to
        // render a raw id and no lazy association escapes.
        String aadeCode = type.getAadeInvoiceTypeId() == null ? null
                : aadeInvoiceTypes.find(type.getAadeInvoiceTypeId())
                        .map(AadeInvoiceTypeView::code)
                        .orElse(null);

        return new SalesDocumentTypeView(
                type.getId(),
                type.getDescription(),
                type.getAffectsStock(),
                type.getTransfersStock(),
                type.isRequiresMydataTransmission(),
                type.getAadeInvoiceTypeId(),
                aadeCode,
                type.isActive());
    }
}
