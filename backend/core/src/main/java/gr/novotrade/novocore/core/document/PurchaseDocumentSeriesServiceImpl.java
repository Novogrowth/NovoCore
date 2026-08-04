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
        return repository.findById(id).map(this::toView);
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
    public PurchaseDocumentSeriesView changeAbbreviation(long id, String abbreviation) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
        if (abbreviation == null || abbreviation.isBlank()) {
            throw new InvalidDocumentSeriesException("An abbreviation must not be blank.");
        }
        String corrected = abbreviation.trim();
        if (corrected.equals(series.getAbbreviation())) {
            return toView(series);
        }
        requireNothingRecorded(series, "abbreviation");
        if (repository.existsByAbbreviationIgnoreCase(corrected)) {
            throw new InvalidDocumentSeriesException(
                    "A purchase document series abbreviated \"" + corrected
                            + "\" already exists. The abbreviation is what appears on the "
                            + "document, so two series cannot share one.");
        }

        String previous = series.getAbbreviation();
        series.changeAbbreviation(corrected);
        auditLog.record("purchase-document-series.abbreviation-changed", ENTITY_TYPE,
                String.valueOf(id),
                Map.of("previousAbbreviation", previous, "abbreviation", corrected));
        return toView(series);
    }

    @Override
    @Transactional
    public PurchaseDocumentSeriesView changeDocumentType(long id, long documentTypeId) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
        if (series.getDocumentType().getId() == documentTypeId) {
            return toView(series);
        }
        requireNothingRecorded(series, "document type");

        PurchaseDocumentType documentType = documentTypes.findById(documentTypeId)
                .orElseThrow(() -> DocumentTypeNotFoundException.purchase(documentTypeId));

        long previous = series.getDocumentType().getId();
        series.changeDocumentType(documentType);
        auditLog.record("purchase-document-series.document-type-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of(
                        "previousDocumentTypeId", String.valueOf(previous),
                        "documentTypeId", String.valueOf(documentTypeId)));
        return toView(series);
    }

    @Override
    @Transactional
    public PurchaseDocumentSeriesView changeGetsMark(long id, boolean getsMark) {
        PurchaseDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.purchase(id));
        if (series.isGetsMark() == getsMark) {
            return toView(series);
        }
        requireNothingRecorded(series, "ΜΑΡΚ flag");

        series.changeGetsMark(getsMark);
        auditLog.record("purchase-document-series.gets-mark-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of("getsMark", String.valueOf(getsMark)));
        return toView(series);
    }

    /**
     * ⚠️⚠️ <strong>This cannot fire today, and the reason is structural rather than about the
     * data.</strong> Measured 2026-08-04: no table in this schema has a foreign key to
     * {@code purchase_document_series} except its own transformation target, so nothing can ever
     * name a purchase series until <strong>F6</strong> gives a purchase document one.
     *
     * <p>It is written now anyway, in the same shape as the sales one, so that F6 adds a query
     * rather than discovering that a whole guard is missing. {@code DocumentReferenceGraphIT} pins
     * the referencing set, so the day that column arrives the build goes red here — which is the
     * remedy {@code CLAUDE.md} prescribes after R1b's per-series key agreed with the global one
     * only because every row's series happened to be null.
     */
    private void requireNothingRecorded(PurchaseDocumentSeries series, String field) {
        if (isNamedByARecordedDocument(series.getId())) {
            throw new InvalidDocumentSeriesException(
                    "The " + field + " of series \"" + series.getAbbreviation() + "\" cannot be "
                            + "changed, because purchase documents have already been recorded in "
                            + "it. Create a new series and deactivate this one.");
        }
    }

    /** @see #requireNothingRecorded — false by construction until F6, not by coincidence. */
    private boolean isNamedByARecordedDocument(long seriesId) {
        return false;
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

    private List<PurchaseDocumentSeriesView> toViews(List<PurchaseDocumentSeries> series) {
        return series.stream().map(this::toView).toList();
    }

    /**
     * ⚠️ The document type's description is read here, <strong>inside the transaction</strong>, and
     * materialised into the view. The association is lazy, so returning the entity and letting a
     * caller reach through it would blow up on first access outside the transaction — the trap
     * {@code CLAUDE.md} names beside proxy self-invocation.
     *
     * <p>Unlike the sales side there is no batched form of the {@code inUse} question, because there
     * is no query behind it to batch.
     */
    private PurchaseDocumentSeriesView toView(PurchaseDocumentSeries series) {
        PurchaseDocumentType type = series.getDocumentType();
        return new PurchaseDocumentSeriesView(
                series.getId(),
                series.getAbbreviation(),
                series.getDescription(),
                type.getId(),
                type.getDescription(),
                series.isGetsMark(),
                series.getTransformableIntoSeriesId(),
                isNamedByARecordedDocument(series.getId()),
                series.isActive());
    }
}
