package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.document.DocumentSeriesNotFoundException;
import gr.novotrade.novocore.core.api.document.DocumentTypeNotFoundException;
import gr.novotrade.novocore.core.api.document.InvalidDocumentSeriesException;
import gr.novotrade.novocore.core.api.document.NewPurchaseDocumentSeries;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ⚠️ No {@code changeChannel} here, and its absence is the same decision as the missing column:
 * channel is where a <em>sale</em> came from and never applies to a purchase.
 */
@Service
class PurchaseDocumentSeriesServiceImpl implements PurchaseDocumentSeriesService {

    private static final String ENTITY_TYPE = "PurchaseDocumentSeries";

    private final PurchaseDocumentSeriesRepository repository;
    private final PurchaseDocumentTypeRepository documentTypes;
    private final AuditLogService auditLog;

    PurchaseDocumentSeriesServiceImpl(PurchaseDocumentSeriesRepository repository,
            PurchaseDocumentTypeRepository documentTypes, AuditLogService auditLog) {
        this.repository = repository;
        this.documentTypes = documentTypes;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDocumentSeriesView> all() {
        return toViews(repository.findAllByOrderByAbbreviationAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDocumentSeriesView> active() {
        return toViews(repository.findByActiveTrueOrderByAbbreviationAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDocumentSeriesView> ofDocumentType(long documentTypeId) {
        return toViews(repository.findByDocumentTypeIdOrderByAbbreviationAsc(documentTypeId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseDocumentSeriesView> find(long id) {
        return repository.findById(id).map(PurchaseDocumentSeriesServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseDocumentSeriesView require(long id) {
        return find(id).orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
    }

    @Override
    @Transactional
    public PurchaseDocumentSeriesView create(NewPurchaseDocumentSeries request) {
        String abbreviation = request.abbreviation().trim();
        if (repository.existsByAbbreviationIgnoreCase(abbreviation)) {
            throw new InvalidDocumentSeriesException(
                    "A purchase document series abbreviated \"" + abbreviation
                            + "\" already exists. The abbreviation is what appears on the "
                            + "document, so two series cannot share one.");
        }

        PurchaseDocumentType documentType = documentTypes.findById(request.documentTypeId())
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(request.documentTypeId()));

        requireTargetExists(request.transformableIntoSeriesId());

        PurchaseDocumentSeries saved = repository.save(new PurchaseDocumentSeries(
                abbreviation,
                request.description().trim(),
                documentType,
                request.getsMark(),
                request.transformableIntoSeriesId()));

        auditLog.record("purchase-document-series.created", ENTITY_TYPE,
                String.valueOf(saved.getId()), Map.of(
                        "abbreviation", abbreviation,
                        "documentTypeId", String.valueOf(request.documentTypeId()),
                        "getsMark", String.valueOf(request.getsMark())));

        return toView(saved);
    }

    @Override
    @Transactional
    public PurchaseDocumentSeriesView describe(long id, String description) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
        if (description == null || description.isBlank()) {
            throw new InvalidDocumentSeriesException("A description must not be blank.");
        }
        String corrected = description.trim();
        if (corrected.equals(series.getDescription())) {
            return toView(series);
        }

        String previous = series.getDescription();
        series.describe(corrected);
        auditLog.record("purchase-document-series.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(series);
    }

    @Override
    @Transactional
    public PurchaseDocumentSeriesView mapTransformationTarget(long id, Long targetSeriesId) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));

        if (targetSeriesId != null && targetSeriesId.equals(id)) {
            throw new InvalidDocumentSeriesException(
                    "A series cannot transform into itself. Transformation exists so a document "
                            + "recorded in the wrong series can be moved to the right one in a "
                            + "single action; a self-reference is a rule that does nothing while "
                            + "reading as configuration.");
        }
        requireTargetExists(targetSeriesId);

        series.mapTransformationTarget(targetSeriesId);
        auditLog.record("purchase-document-series.transformation-target-mapped", ENTITY_TYPE,
                String.valueOf(id), Map.of("transformableIntoSeriesId",
                        targetSeriesId == null ? "(none)" : String.valueOf(targetSeriesId)));
        return toView(series);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
        if (!series.isActive()) {
            return;
        }
        series.setActive(false);
        auditLog.record("purchase-document-series.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", series.getAbbreviation()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
        if (series.isActive()) {
            return;
        }
        series.setActive(true);
        auditLog.record("purchase-document-series.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", series.getAbbreviation()));
    }

    private void requireTargetExists(Long targetSeriesId) {
        if (targetSeriesId != null && !repository.existsById(targetSeriesId)) {
            throw DocumentSeriesNotFoundException.purchase(targetSeriesId);
        }
    }

    private static List<PurchaseDocumentSeriesView> toViews(List<PurchaseDocumentSeries> series) {
        return series.stream().map(PurchaseDocumentSeriesServiceImpl::toView).toList();
    }

    /**
     * ⚠️ The document type's description is read here, <strong>inside the transaction</strong>, and
     * materialised into the view. The association is lazy, so returning the entity and letting a
     * caller reach through it would blow up on first access outside the transaction — the trap
     * {@code CLAUDE.md} names beside proxy self-invocation.
     */
    private static PurchaseDocumentSeriesView toView(PurchaseDocumentSeries series) {
        PurchaseDocumentType type = series.getDocumentType();
        return new PurchaseDocumentSeriesView(
                series.getId(),
                series.getAbbreviation(),
                series.getDescription(),
                type.getId(),
                type.getDescription(),
                series.isGetsMark(),
                series.getTransformableIntoSeriesId(),
                series.isActive());
    }
}
