package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * ⚠️ <strong>A pin, not a test of behaviour. It exists to go red in somebody else's step.</strong>
 *
 * <h2>The failure it is built against</h2>
 *
 * <p>R2 gave three records an {@code inUse} flag and seven routes a freeze that depends on it.
 * <strong>On two of the three, the freeze cannot fire</strong>: measured 2026-08-04, the only
 * foreign key referencing {@code purchase_document_series} is its own transformation target, and
 * <strong>nothing at all</strong> references {@code delivery_method}. Their guards are written,
 * shaped like the sales one, and unreachable.
 *
 * <p>That is the exact shape {@code CLAUDE.md} names after R1b, one step earlier and one layer down.
 * R1a made the document-number key per-series while the service checked globally; the two agreed
 * <em>perfectly</em>, because every row's {@code series_id} was null. The migration said so and
 * treated it as proof the change was safe. It was true, and it was also why the divergence was
 * invisible — until R1b gave an invoice a series and the database allowed what the service refused.
 *
 * <p><strong>The rule that came out of it:</strong> when something is justified by "this is
 * identical for every existing row", that justification is also a list of the places that will
 * silently disagree once the data stops looking like that. <em>Write the list down at the time.</em>
 * This class is that list, in a form that fails a build.
 *
 * <h2>What to do when this test goes red</h2>
 *
 * <p>Nothing is broken. A new foreign key has arrived, which means a document can now name one of
 * these rows, which means the corresponding {@code isNamedByARecordedDocument} is no longer honest:
 *
 * <ul>
 *   <li>{@code purchase_document_series} — expected at <strong>F6</strong>, which makes
 *       {@code purchase_document_type} mandatory on a purchase document. Replace
 *       {@code PurchaseDocumentSeriesServiceImpl.isNamedByARecordedDocument}'s {@code return false}
 *       with the query {@code SalesDocumentSeriesRepository} already has, and add the batched form
 *       so the list screen does not become an N+1.
 *   <li>{@code delivery_method} — expected at <strong>18b</strong>, the dispatch document. Same
 *       change in {@code DeliveryMethodServiceImpl}.
 * </ul>
 *
 * <p>Then extend {@code R2ReferenceDataContractIT}: its purchase and delivery-method tests assert
 * only the <em>correction</em> half today, because the refusal half is unreachable. It becomes
 * reachable on the same day this test does.
 *
 * <h2>⚠️ Why this reads the catalogue rather than the migrations</h2>
 *
 * <p>A grep over {@code V*.sql} would answer what the migrations <em>say</em>. This asks the
 * database what it <em>has</em>, after every migration has run — which is the distinction
 * {@code CLAUDE.md} draws under <em>a fact established by reading, then built upon</em>. A foreign
 * key added by a later migration, or renamed, or dropped and re-added, is invisible to the first and
 * unmissable to the second.
 */
@SpringBootTest(properties = {
    "spring.datasource.password=overridden-by-testcontainers",
    // The application refuses to start with no accounts and no initial owner, deliberately — it is
    // better to fail at boot than to run something nobody can log in to. This class never logs in;
    // it only needs the migrations to have run against a real PostgreSQL.
    "novocore.bootstrap.owner-username=graph.owner",
    "novocore.bootstrap.owner-password=owner-password-long-enough",
})
@Import(PostgresTestContainerConfiguration.class)
class DocumentReferenceGraphIT {

    @Autowired private DataSource dataSource;

    /**
     * Every foreign key pointing at {@code table}, as {@code referencing_table.column}.
     *
     * <p>Deliberately includes self-references: a series' transformation target is a real reference
     * and leaving it out would make the assertion's expected set smaller than the truth, which is
     * the direction that hides things.
     */
    private List<String> referencesTo(String table) {
        String sql = """
                SELECT c.conrelid::regclass::text || '.' || a.attname
                FROM pg_constraint c
                JOIN unnest(c.conkey) WITH ORDINALITY k(attnum, ord) ON true
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                WHERE c.contype = 'f' AND c.confrelid = ?::regclass
                ORDER BY 1
                """;
        List<String> found = new ArrayList<>();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    found.add(rows.getString(1));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read the foreign keys of " + table, e);
        }
        return found;
    }

    @Test
    @DisplayName("⚠️ NOTHING references delivery_method — so its freeze is unreachable. Read the class javadoc")
    void nothingReferencesADeliveryMethod() {
        assertThat(referencesTo("delivery_method"))
                .as("""
                        A foreign key to delivery_method has appeared. DeliveryMethodView.inUse is \
                        now a lie and DeliveryMethodServiceImpl.isNamedByARecordedDocument still \
                        returns a hard-coded false. See this class's javadoc — this failure is the \
                        whole reason it exists.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠️ only its own transform target references purchase_document_series — F6 changes this")
    void onlyItselfReferencesAPurchaseSeries() {
        assertThat(referencesTo("purchase_document_series"))
                .as("""
                        A foreign key to purchase_document_series has appeared — almost certainly \
                        F6 giving a purchase document one. PurchaseDocumentSeriesView.inUse is now \
                        a lie and the freeze on abbreviation/documentTypeId/getsMark is enforced by \
                        nothing. See this class's javadoc.""")
                .containsExactly("purchase_document_series.transformable_into_series_id");
    }

    @Test
    @DisplayName("⚠️ R4/A.9 — payment_method IS referenced, so ITS freeze is reachable too")
    void paymentMethodIsReferenced() {
        // ⭐ WHY THIS ROW EXISTS AT ALL, and why it could not before R4. Until V37, NOTHING in the
        // database referenced payment_method: sales_invoice carried the enum NAME in a varchar with
        // a CHECK, so "has this method been used" was a string comparison and the freeze predicate
        // was not expressible as a reference. V37 made it a foreign key, which is what made this
        // pinnable.
        //
        // ⚠️ It is the POSITIVE control for the two emptiness assertions in this class, and that
        // matters more than the assertion itself: "nothing references delivery_method" and "nothing
        // references purchase_document_series" would pass just as happily against a typo in the SQL,
        // a wrong catalogue column, or a connection to an empty schema. A non-empty expected set
        // proves the query can see references at all.
        assertThat(referencesTo("payment_method"))
                .as("""
                        The reference from sales_invoice to payment_method has gone.                         PaymentMethodView.inUse and PaymentMethodServiceImpl.requireNothingRecorded                         both depend on it: without a foreign key the freeze silently stops being                         expressible, which is the state R4 found this table in.""")
                .containsExactly("sales_invoice.payment_method_id");
    }

    @Test
    @DisplayName("⚠️ R4/A.9 — the codification is referenced BY the business list, which is the two-layer shape")
    void theCodificationIsReferencedByTheBusinessList() {
        // The whole R4 correction in one assertion: payment methods are a BUSINESS list that
        // REFERENCES an AADE codification. If this reference disappears, somebody has collapsed the
        // two layers back into one — which is the mistake R1a had to correct for document types and
        // R4 had to correct here.
        assertThat(referencesTo("aade_payment_method"))
                .containsExactly("payment_method.aade_payment_method_id");
    }

    @Test
    @DisplayName("the sales series' freeze IS reachable, and this is what makes the other two meaningful")
    void aSalesSeriesIsReferencedByAnInvoice() {
        // ⚠️ The positive control. Without it the two assertions above would pass just as happily
        // against a query that returns nothing for every table — including a typo in the SQL, a
        // wrong catalogue column, or a connection to an empty schema. This is the case that must
        // find something, and it is why an empty result above means "nothing references it" rather
        // than "this test measures nothing".
        assertThat(referencesTo("sales_document_series"))
                .containsExactly(
                        "sales_document_series.transformable_into_series_id",
                        "sales_invoice.series_id");
    }
}
