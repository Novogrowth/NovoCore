package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.document.DocumentSeriesNotFoundException;
import gr.novotrade.novocore.core.api.document.DocumentTypeNotFoundException;
import gr.novotrade.novocore.core.api.document.InvalidDocumentSeriesException;
import gr.novotrade.novocore.core.api.document.NewSalesDocumentSeries;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesView;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SalesDocumentSeriesServiceImpl implements SalesDocumentSeriesService {

    private static final String ENTITY_TYPE = "SalesDocumentSeries";

    private final SalesDocumentSeriesRepository repository;
    private final SalesDocumentTypeRepository documentTypes;
    private final AuditLogService auditLog;

    SalesDocumentSeriesServiceImpl(SalesDocumentSeriesRepository repository,
            SalesDocumentTypeRepository documentTypes, AuditLogService auditLog) {
        this.repository = repository;
        this.documentTypes = documentTypes;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesDocumentSeriesView> all() {
        return toViews(repository.findAllByOrderByAbbreviationAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesDocumentSeriesView> active() {
        return toViews(repository.findByActiveTrueOrderByAbbreviationAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesDocumentSeriesView> ofDocumentType(long documentTypeId) {
        return toViews(repository.findByDocumentTypeIdOrderByAbbreviationAsc(documentTypeId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesDocumentSeriesView> find(long id) {
        return repository.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesDocumentSeriesView require(long id) {
        return find(id).orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView create(NewSalesDocumentSeries request) {
        String abbreviation = request.abbreviation().trim();
        if (repository.existsByAbbreviationIgnoreCase(abbreviation)) {
            throw new InvalidDocumentSeriesException(
                    "A sales document series abbreviated \"" + abbreviation
                            + "\" already exists. The abbreviation is what appears on the "
                            + "document, so two series cannot share one.");
        }

        SalesDocumentType documentType = documentTypes.findById(request.documentTypeId())
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(request.documentTypeId()));

        requireTargetExists(request.transformableIntoSeriesId());

        SalesDocumentSeries saved = repository.save(new SalesDocumentSeries(
                abbreviation,
                request.description().trim(),
                documentType,
                request.channel(),
                request.getsMark(),
                request.transformableIntoSeriesId()));

        auditLog.record("sales-document-series.created", ENTITY_TYPE,
                String.valueOf(saved.getId()), Map.of(
                        "abbreviation", abbreviation,
                        "documentTypeId", String.valueOf(request.documentTypeId()),
                        // "(none)" rather than omitted: a series with no channel is a real,
                        // meaningful state (self-supply), not a field somebody skipped.
                        "channel", request.channel() == null
                                ? "(none)" : request.channel().name(),
                        "getsMark", String.valueOf(request.getsMark())));

        return toView(saved);
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView describe(long id, String description) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
        if (description == null || description.isBlank()) {
            throw new InvalidDocumentSeriesException("A description must not be blank.");
        }
        String corrected = description.trim();
        if (corrected.equals(series.getDescription())) {
            return toView(series);
        }

        String previous = series.getDescription();
        series.describe(corrected);
        auditLog.record("sales-document-series.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(series);
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView changeAbbreviation(long id, String abbreviation) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
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
                    "A sales document series abbreviated \"" + corrected
                            + "\" already exists. The abbreviation is what appears on the "
                            + "document, so two series cannot share one.");
        }

        String previous = series.getAbbreviation();
        series.changeAbbreviation(corrected);
        auditLog.record("sales-document-series.abbreviation-changed", ENTITY_TYPE,
                String.valueOf(id),
                Map.of("previousAbbreviation", previous, "abbreviation", corrected));
        return toView(series);
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView changeDocumentType(long id, long documentTypeId) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
        if (series.getDocumentType().getId() == documentTypeId) {
            return toView(series);
        }
        requireNothingRecorded(series, "document type");

        SalesDocumentType documentType = documentTypes.findById(documentTypeId)
                .orElseThrow(() -> DocumentTypeNotFoundException.sales(documentTypeId));

        long previous = series.getDocumentType().getId();
        series.changeDocumentType(documentType);
        auditLog.record("sales-document-series.document-type-changed", ENTITY_TYPE,
                String.valueOf(id), Map.of(
                        "previousDocumentTypeId", String.valueOf(previous),
                        "documentTypeId", String.valueOf(documentTypeId)));
        return toView(series);
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView changeGetsMark(long id, boolean getsMark) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
        if (series.isGetsMark() == getsMark) {
            return toView(series);
        }
        requireNothingRecorded(series, "ΜΑΡΚ flag");

        series.changeGetsMark(getsMark);
        auditLog.record("sales-document-series.gets-mark-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("getsMark", String.valueOf(getsMark)));
        return toView(series);
    }

    /**
     * ⚠️ The R2 freeze, in one place because all three fields share one predicate.
     *
     * <p>A no-op change is returned before this is reached, so correcting a field to the value it
     * already holds never produces a refusal — which matters on a screen that sends a field on blur.
     */
    private void requireNothingRecorded(SalesDocumentSeries series, String field) {
        if (repository.isNamedByARecordedDocument(series.getId())) {
            throw new InvalidDocumentSeriesException(
                    "The " + field + " of series \"" + series.getAbbreviation() + "\" cannot be "
                            + "changed, because sales documents have already been recorded in it. "
                            + "The abbreviation appears on those documents, the document type "
                            + "decides whether recording them consumed inventory, and the ΜΑΡΚ flag "
                            + "asserts something about documents that already exist. Create a new "
                            + "series and deactivate this one.");
        }
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView changeChannel(long id, SalesChannel channel) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));

        // Null is accepted and is not a "clear the field" convenience: a series that is not a
        // sales channel is a real configuration, and R1b reads this value as the invoice's channel.
        series.changeChannel(channel);
        auditLog.record("sales-document-series.channel-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("channel", channel == null ? "(none)" : channel.name()));
        return toView(series);
    }

    @Override
    @Transactional
    public SalesDocumentSeriesView mapTransformationTarget(long id, Long targetSeriesId) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));

        if (targetSeriesId != null && targetSeriesId.equals(id)) {
            throw new InvalidDocumentSeriesException(
                    "A series cannot transform into itself. Transformation exists so a document "
                            + "recorded in the wrong series can be moved to the right one in a "
                            + "single action; a self-reference is a rule that does nothing while "
                            + "reading as configuration.");
        }
        requireTargetExists(targetSeriesId);

        series.mapTransformationTarget(targetSeriesId);
        auditLog.record("sales-document-series.transformation-target-mapped", ENTITY_TYPE,
                String.valueOf(id), Map.of("transformableIntoSeriesId",
                        targetSeriesId == null ? "(none)" : String.valueOf(targetSeriesId)));
        return toView(series);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
        if (!series.isActive()) {
            return;
        }
        series.setActive(false);
        auditLog.record("sales-document-series.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", series.getAbbreviation()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        SalesDocumentSeries series = repository.findById(id)
                .orElseThrow(() -> DocumentSeriesNotFoundException.sales(id));
        if (series.isActive()) {
            return;
        }
        series.setActive(true);
        auditLog.record("sales-document-series.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", series.getAbbreviation()));
    }

    private void requireTargetExists(Long targetSeriesId) {
        if (targetSeriesId != null && !repository.existsById(targetSeriesId)) {
            throw DocumentSeriesNotFoundException.sales(targetSeriesId);
        }
    }

    /**
     * ⚠️ <strong>One query for the whole list's {@code inUse} flags, not one per row.</strong> A
     * list screen renders every series with its own locked state, so the per-row form of this
     * question would be an N+1 on the page that exists to be scanned.
     */
    private List<SalesDocumentSeriesView> toViews(List<SalesDocumentSeries> series) {
        Set<Long> recorded = repository.idsNamedByARecordedDocument();
        return series.stream().map(one -> toView(one, recorded.contains(one.getId()))).toList();
    }

    private SalesDocumentSeriesView toView(SalesDocumentSeries series) {
        return toView(series, repository.isNamedByARecordedDocument(series.getId()));
    }

    /**
     * ⚠️ The document type's description is read here, <strong>inside the transaction</strong>, and
     * materialised into the view. The association is lazy, so returning the entity and letting a
     * caller reach through it would blow up on first access outside the transaction — the trap
     * {@code CLAUDE.md} names beside proxy self-invocation.
     *
     * <p>{@code inUse} is passed in rather than looked up here, so the list form can answer it for
     * every row at once.
     */
    private static SalesDocumentSeriesView toView(SalesDocumentSeries series, boolean inUse) {
        SalesDocumentType type = series.getDocumentType();
        return new SalesDocumentSeriesView(
                series.getId(),
                series.getAbbreviation(),
                series.getDescription(),
                type.getId(),
                type.getDescription(),
                series.getChannel(),
                series.isGetsMark(),
                series.getTransformableIntoSeriesId(),
                inUse,
                series.isActive());
    }
}
