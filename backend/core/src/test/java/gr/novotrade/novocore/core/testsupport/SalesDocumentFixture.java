package gr.novotrade.novocore.core.testsupport;

import gr.novotrade.novocore.core.api.document.NewSalesDocumentSeries;
import gr.novotrade.novocore.core.api.document.NewSalesDocumentType;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeService;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The sales document types and series an integration test needs in order to record a sale.
 *
 * <h2>Why this exists rather than a dev seed</h2>
 *
 * <p>R1b makes {@code NewSalesInvoice.seriesId} mandatory, so every test that records a sale needs a
 * series to exist. The obvious answer is to seed a few — and it is the wrong one. {@code V31} ships
 * {@code sales_document_type} and {@code sales_document_series} <strong>empty</strong> on purpose:
 * the owner authors those rows himself in R2, choosing each AADE invoice type, and a seed would put
 * rows in front of him that he did not create. So tests build their own, exactly as they already
 * build their own customers, products and VAT classes. <strong>No migration, no dev seed, and S.4
 * stays deferred to R2 in full.</strong>
 *
 * <h2>Every fixture is namespaced, because nothing rolls back</h2>
 *
 * <p>{@code sales_document_type.description} and {@code sales_document_series.abbreviation} are both
 * {@code UNIQUE} across the whole table, and core integration tests share one database with no
 * rollback between them. The {@code prefix} is what keeps two test classes from colliding; pass a
 * short one, since an abbreviation is {@code varchar(20)}.
 *
 * <h2>What the three shapes are for</h2>
 *
 * <ul>
 *   <li>{@link #stockMoving} — the ΑΛΠ / ΤΠΔΑ shape: sale and transport in one document, so stock
 *       moves. This is what every pre-R1b test was implicitly assuming, and using it is what makes
 *       those tests' assertions still true.
 *   <li>{@link #nonStockMoving} — the plain Τιμολόγιο shape: purely a sale, goods leave later on a
 *       dispatch document (18b). Recording one consumes nothing at all.
 *   <li>{@link #channelLess} — the self-supply shape. The customer is the issuer, so there is no
 *       channel to attribute revenue to, and recording against it is <em>refused</em>.
 * </ul>
 *
 * <p>⚠️ Each shape is created once and cached. Creating a second type with the same description
 * would be refused as a duplicate, and a test that quietly got a different series each time would be
 * asserting against something other than what it thought.
 */
public final class SalesDocumentFixture {

    /**
     * ⚠️ Sort codes are unique per table. These fixtures assert nothing about ORDER — the value is
     * arbitrary and only has to be distinct, which is exactly why an arbitrary one is safe here.
     */
    private static final java.util.concurrent.atomic.AtomicInteger SORT_CODES =
            new java.util.concurrent.atomic.AtomicInteger(9000);

    private final SalesDocumentTypeService types;
    private final SalesDocumentSeriesService series;
    private final String prefix;

    /** Series ids by cache key, so a shape is created once per test class. */
    private final Map<String, Long> cache = new LinkedHashMap<>();

    public SalesDocumentFixture(SalesDocumentTypeService types, SalesDocumentSeriesService series,
            String prefix) {
        this.types = types;
        this.series = series;
        this.prefix = prefix;
    }

    /** A series in this channel whose document type moves stock — the ΑΛΠ shape. */
    public long stockMoving(SalesChannel channel) {
        return seriesFor("SM-" + channel.name(), true, channel);
    }

    /**
     * A series in this channel whose document type does <strong>not</strong> move stock.
     *
     * <p>The plain Τιμολόγιο: revenue posts, the ledger balances, and no {@code stock_consumption}
     * row is created at all.
     */
    public long nonStockMoving(SalesChannel channel) {
        return seriesFor("NS-" + channel.name(), false, channel);
    }

    /**
     * A series with no sales channel — self-supply's shape, which recording must refuse.
     *
     * <p>Its type moves stock, deliberately: that removes stock behaviour as an explanation for the
     * refusal, so a test using this is testing the channel rule and nothing else.
     */
    public long channelLess() {
        return seriesFor("CL", true, null);
    }

    /** The document type behind {@link #stockMoving}, for tests that deactivate it. */
    public long stockMovingTypeId(SalesChannel channel) {
        return series.require(stockMoving(channel)).documentTypeId();
    }

    /**
     * The series for this shape, created once and then found.
     *
     * <p>⚠️ <strong>Found before created, and the in-memory cache is only an optimisation.</strong>
     * JUnit builds a new test-class instance per test method, so a purely in-memory cache is empty
     * every time and the second method would ask for a type whose description already exists — which
     * {@code SalesDocumentTypeService.create} correctly refuses. The database is the only thing that
     * survives between methods here (these tests deliberately do not roll back), so it has to be
     * what answers "does this already exist".
     */
    private long seriesFor(String key, boolean affectsStock, SalesChannel channel) {
        Long memo = cache.get(key);
        if (memo != null) {
            return memo;
        }
        String abbreviation = abbreviation(key);
        long id = series.all().stream()
                .filter(existing -> existing.abbreviation().equals(abbreviation))
                .mapToLong(existing -> existing.id())
                .findFirst()
                .orElseGet(() -> create(key, abbreviation, affectsStock, channel));
        cache.put(key, id);
        return id;
    }

    private long create(String key, String abbreviation, boolean affectsStock,
            SalesChannel channel) {
        long typeId = types.create(NewSalesDocumentType.decided(
                prefix + " " + key + " type",
                affectsStock,
                // transfersStock mirrors affectsStock: a document that hands goods over
                // necessarily consumes them, and V31's CHECK refuses the incoherent pair.
                affectsStock,
                true,
                null, SORT_CODES.incrementAndGet())).id();
        return series.create(new NewSalesDocumentSeries(
                abbreviation, prefix + " " + key + " series", typeId, channel, false, null, SORT_CODES.incrementAndGet())).id();
    }

    /**
     * A unique abbreviation within {@code varchar(20)}.
     *
     * <p>Channel names are long enough that {@code prefix + "-SM-STORE_AND_PHONE"} overflows, so the
     * key is compressed rather than truncated — truncation would silently make two shapes collide on
     * one abbreviation and fail as a duplicate somewhere unrelated.
     */
    private String abbreviation(String key) {
        String compact = key
                .replace("STORE_AND_PHONE", "S")
                .replace("ECOMMERCE", "E")
                .replace("SKROUTZ", "K");
        return prefix + "-" + compact;
    }
}
