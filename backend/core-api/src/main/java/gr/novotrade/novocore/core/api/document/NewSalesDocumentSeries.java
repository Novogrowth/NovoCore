package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to add a sales document series.
 *
 * @param channel null means this series is not a sales channel — see
 *     {@link SalesDocumentSeriesView#channel()}. R1 <em>references</em> the existing
 *     {@link SalesChannel}; it does not create the concept, which has reached the ledger since
 *     step 3 with the Sales and Sales-returns accounts already split per channel.
 * @param transformableIntoSeriesId null where no transformation target has been configured. A
 *     series may not transform into itself.
 *
 * @param sortCode ⚠️ Ordering only — see the view. <strong>Required</strong>, because the
 *     column is {@code NOT NULL}: unlike {@code sales_invoice.series_id}, a sort code has no
 *     truth value, so giving one fabricates nothing.
 *
 * @param abbreviation ⚠️ <strong>AN ACCENT OR A LOWERCASE LETTER HERE RE-OPENS CLOSED COLLATION
 *     WORK — roadmap F5b. Read this before choosing one.</strong>
 *     <p>A document number is this abbreviation followed by a zero-padded integer with no
 *     separator ({@code ΑΛΠ00000087}), and {@code DOCUMENT_NUMBER} is the only text sort key the
 *     surface ships. {@code F5b} — an {@code ORDER BY … COLLATE "el-GR-x-icu"} on that column —
 *     was <strong>closed as NOT NEEDED on 2026-08-06</strong> on one measured fact: for
 *     <strong>plain uppercase unaccented Greek</strong>, byte order under this deployment's
 *     {@code --locale=C} and {@code el-GR-x-icu} produce the <em>same</em> order, because the
 *     Greek uppercase block is contiguous and alphabetical. Measured against the live database
 *     with three negative controls, all of which differed: an <strong>accented</strong> capital
 *     ({@code ΆΛΦΑ}), <strong>mixed case</strong> ({@code αλπ}), and Greek beside Latin.
 *     <p>⚠️ <strong>So the closure is conditional on a fact about DATA, and this field is where
 *     that fact is decided.</strong> The owner confirmed on 2026-08-06 that every real series
 *     prefix is plain uppercase Greek. <strong>The first one that is not brings the collation work
 *     back.</strong>
 *     <p>⚠️ <strong>NOTHING ENFORCES THIS, and that is stated rather than fixed.</strong> Measured
 *     2026-08-06: the column is {@code varchar(20)} with only <em>not-blank</em> and
 *     <em>unique</em> CHECKs, the guard here is {@code Required.text} (non-blank), and the screen
 *     applies no pattern. <strong>Any text is storable.</strong> No constraint was proposed — the
 *     residual is recorded so whoever meets it can decide, not pre-empted by a rule nobody asked
 *     for.
 */
public record NewSalesDocumentSeries(
        @Mandatory String abbreviation,
        @Mandatory String description,
        @Mandatory Long documentTypeId,
        SalesChannel channel,
        @Mandatory Boolean getsMark,
        Long transformableIntoSeriesId,
        @Mandatory Integer sortCode) {

    public NewSalesDocumentSeries {
        Required.field(sortCode, "sortCode");
        Required.text(abbreviation, "abbreviation");
        Required.text(description, "description");
        Required.field(documentTypeId, "documentTypeId");
        Required.field(getsMark, "getsMark");
    }
}
