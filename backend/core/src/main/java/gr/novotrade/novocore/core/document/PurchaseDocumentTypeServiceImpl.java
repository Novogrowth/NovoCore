package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeView;
import gr.novotrade.novocore.core.api.document.DocumentTypeNotFoundException;
import gr.novotrade.novocore.core.api.document.InvalidDocumentTypeException;
import gr.novotrade.novocore.core.api.document.NewPurchaseDocumentType;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentTypeService;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentTypeView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PurchaseDocumentTypeServiceImpl implements PurchaseDocumentTypeService {

    private static final String ENTITY_TYPE = "PurchaseDocumentType";

    private final PurchaseDocumentTypeRepository repository;
    private final AadeInvoiceTypeService aadeInvoiceTypes;
    private final AuditLogService auditLog;

    PurchaseDocumentTypeServiceImpl(PurchaseDocumentTypeRepository repository,
            AadeInvoiceTypeService aadeInvoiceTypes, AuditLogService auditLog) {
        this.repository = repository;
        this.aadeInvoiceTypes = aadeInvoiceTypes;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDocumentTypeView> all() {
        return toViews(repository.findAllByOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDocumentTypeView> active() {
        return toViews(repository.findByActiveTrueOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDocumentTypeView> drafts() {
        return toViews(repository
                .findByAffectsStockIsNullOrTransfersStockIsNullOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseDocumentTypeView> find(long id) {
        return repository.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseDocumentTypeView require(long id) {
        return find(id).orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
    }

    @Override
    @Transactional
    public PurchaseDocumentTypeView create(NewPurchaseDocumentType request) {
        String description = request.description().trim();
        requireDescriptionIsFree(description, null);
        requireCompatibleStockBehaviour(request.affectsStock(), request.transfersStock());
        requireRecipientSide(request.aadeInvoiceTypeId());

        // A type whose stock behaviour is undecided is created INACTIVE — a draft. Refusing
        // instead would make it impossible to save a type before the stock question is answered,
        // and defaulting a flag to false would record a decision nobody took. The database CHECK
        // says the same thing from the other side and cannot be bypassed by a second write path.
        boolean decided = request.affectsStock() != null && request.transfersStock() != null;

        PurchaseDocumentType saved = repository.save(new PurchaseDocumentType(
                description,
                request.affectsStock(),
                request.transfersStock(),
                request.requiresMydataTransmission(),
                request.aadeInvoiceTypeId(),
                request.sortCode(),
                decided));

        auditLog.record("purchase-document-type.created", ENTITY_TYPE,
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
    public PurchaseDocumentTypeView describe(long id, String description) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
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
        auditLog.record("purchase-document-type.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(type);
    }


    @Override
    @Transactional
    public PurchaseDocumentTypeView changeSortCode(long id, int sortCode) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
        if (type.getSortCode() == sortCode) {
            return toView(type);
        }
        requireSortCodeIsFree(sortCode);

        int previous = type.getSortCode();
        type.changeSortCode(sortCode);
        auditLog.record("purchase-document-type.sort-code-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousSortCode", String.valueOf(previous),
                        "sortCode", String.valueOf(sortCode)));
        return toView(type);
    }

    @Override
    @Transactional
    public PurchaseDocumentTypeView changeStockBehaviour(
            long id, boolean affectsStock, boolean transfersStock) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
        requireCompatibleStockBehaviour(affectsStock, transfersStock);

        type.changeStockBehaviour(affectsStock, transfersStock);
        auditLog.record("purchase-document-type.stock-behaviour-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of(
                        "affectsStock", String.valueOf(affectsStock),
                        "transfersStock", String.valueOf(transfersStock)));
        return toView(type);
    }

    @Override
    @Transactional
    public PurchaseDocumentTypeView changeMydataTransmissionRequired(long id, boolean required) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
        type.changeMydataTransmissionRequired(required);
        auditLog.record("purchase-document-type.mydata-transmission-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of("requiresMydataTransmission", String.valueOf(required)));
        return toView(type);
    }

    @Override
    @Transactional
    public PurchaseDocumentTypeView mapToAadeInvoiceType(long id, Long aadeInvoiceTypeId) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
        requireRecipientSide(aadeInvoiceTypeId);

        type.mapToAadeInvoiceType(aadeInvoiceTypeId);
        auditLog.record("purchase-document-type.aade-type-mapped", ENTITY_TYPE, String.valueOf(id),
                Map.of("aadeInvoiceTypeId",
                        aadeInvoiceTypeId == null ? "(none)" : String.valueOf(aadeInvoiceTypeId)));
        return toView(type);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
        if (!type.isActive()) {
            return;
        }
        // Deliberately unconditional. Documents already recorded under this type keep pointing at
        // it and stay explicable; deactivating only removes it from what a form offers.
        type.setActive(false);
        auditLog.record("purchase-document-type.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("description", type.getDescription()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        PurchaseDocumentType type = repository.findById(id)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(id));
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
        auditLog.record("purchase-document-type.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("description", type.getDescription()));
    }

    /**
     * ⚠️ Unique so the ordering is deterministic — two rows sharing a sort code would order by
     * whatever the plan produced, which is the one thing a sort key must not do. See {@code V34}.
     */
    private void requireSortCodeIsFree(int sortCode) {
        if (repository.existsBySortCode(sortCode)) {
            throw new InvalidDocumentTypeException(
                    "Sort code " + sortCode + " is already used by another purchase document type. "
                            + "Sort codes are unique so the list has one definite order.");
        }
    }

    private void requireDescriptionIsFree(String description, Long selfId) {
        repository.findByDescriptionIgnoreCase(description).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new InvalidDocumentTypeException(
                        "A purchase document type called \"" + description + "\" already exists.");
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
     * ⚠️ A purchase document type may only name an AADE code from the recipient side of annex 8.1.
     *
     * <p>The mirror of the sales rule, and needed for the same reason: the XSD has ONE enumeration
     * covering both directions, so nothing in AADE's artefacts stops a purchase type from naming
     * "Τιμολόγιο Πώλησης".
     *
     * <p>Both rules also refuse the {@code ENTITY_ADJUSTING} group, which is neither issued nor
     * received — {@link gr.novotrade.novocore.core.api.codification.AadeInvoiceGroup} explains why
     * those six codes belong to no document list at all.
     */
    private void requireRecipientSide(Long aadeInvoiceTypeId) {
        if (aadeInvoiceTypeId == null) {
            return;
        }
        AadeInvoiceTypeView aadeType = aadeInvoiceTypes.require(aadeInvoiceTypeId);
        if (!aadeType.group().hasCounterparty() || aadeType.group().issuedByUs()) {
            throw new InvalidDocumentTypeException(
                    "AADE invoice type " + aadeType.code() + " (" + aadeType.description()
                            + ") is in group " + aadeType.group() + ", which is not a document "
                            + "this business receives. A purchase document type may only name a "
                            + "code from the two recipient groups of annex 8.1.");
        }
    }

    private List<PurchaseDocumentTypeView> toViews(List<PurchaseDocumentType> types) {
        return types.stream().map(this::toView).toList();
    }

    private PurchaseDocumentTypeView toView(PurchaseDocumentType type) {
        // Resolved inside the transaction and materialised into the view, so no caller ever has to
        // render a raw id and no lazy association escapes.
        String aadeCode = type.getAadeInvoiceTypeId() == null ? null
                : aadeInvoiceTypes.find(type.getAadeInvoiceTypeId())
                        .map(AadeInvoiceTypeView::code)
                        .orElse(null);

        return new PurchaseDocumentTypeView(
                type.getId(),
                type.getDescription(),
                type.getAffectsStock(),
                type.getTransfersStock(),
                type.isRequiresMydataTransmission(),
                type.getAadeInvoiceTypeId(),
                aadeCode,
                type.getSortCode(),
                type.isActive());
    }
}
