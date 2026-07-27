package gr.novotrade.novocore.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Asserts the numeric conventions migration V1 documents, against the schema Flyway actually
 * produced.
 *
 * <p>V1 states them as a comment, which is worth exactly as much as whoever reads it. This reads
 * {@code information_schema} instead, so a column added in a later migration with the wrong type
 * fails the build rather than being discovered when a total comes out a cent short.
 *
 * <p><strong>What each rule is worth right now.</strong> The no-floating-point rule is live and
 * meaningful today — it proves the absence of the thing {@code CLAUDE.md} rule 5 forbids across
 * every table that exists. The scale rule became live with {@code vat_class.rate_percent}, the
 * first {@code numeric} column in the schema. There are still no <em>monetary</em> columns: an
 * account's balance is the sum of its journal lines, computed on read and never stored, so the
 * {@code numeric(19,2)} half of the rule starts doing real work when the journal arrives in
 * step 7 — precisely when a mistake would be expensive.
 *
 * <p>Deliberately <em>not</em> asserted: V1 also says a monetary column carries a companion
 * {@code char(3)} currency column. There is no first money column yet, so the naming convention
 * for that companion is not established — asserting a guess at it would either be wrong or
 * silently dictate a naming decision that belongs to step 7.
 */
class SchemaConventionsIT extends AbstractCoreIntegrationTest {

    /** Flyway's own bookkeeping table. Not ours, so not held to our conventions. */
    private static final String NOT_OURS = "flyway_schema_history";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("no column anywhere uses a floating-point or locale-dependent money type")
    void noFloatingPointColumns() {
        List<Map<String, Object>> offenders = jdbc.queryForList("""
                SELECT table_name, column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name <> ?
                  AND data_type IN ('double precision', 'real', 'money')
                ORDER BY table_name, column_name
                """, NOT_OURS);

        assertThat(offenders)
                .as("CLAUDE.md rule 5: money is always BigDecimal, never double or float. "
                        + "double precision and real lose precision invisibly; PostgreSQL's "
                        + "money type is locale-dependent and lossy. There is no exception, so "
                        + "the correct fix is to change the column, not to exempt it here.")
                .isEmpty();
    }

    @Test
    @DisplayName("every numeric column is numeric(19,2) for money or numeric(19,6) for quantity")
    void numericColumnsUseAnApprovedScale() {
        List<Map<String, Object>> offenders = jdbc.queryForList("""
                SELECT table_name, column_name, numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name <> ?
                  AND data_type = 'numeric'
                  AND (numeric_precision IS DISTINCT FROM 19
                       OR numeric_scale NOT IN (2, 6))
                ORDER BY table_name, column_name
                """, NOT_OURS);

        assertThat(offenders)
                .as("Exactly two numeric shapes are allowed: numeric(19,2) for a posted monetary "
                        + "amount, and numeric(19,6) for a multiplier — a quantity, a unit cost, "
                        + "or a rate such as vat_class.rate_percent. The distinction is between "
                        + "an amount, which is two decimals because that is what a cent is, and "
                        + "a multiplier, which must not itself lose precision before the product "
                        + "is rounded once. A third scale means two columns to reconcile by "
                        + "rounding somewhere, which is where cent-level discrepancies come "
                        + "from. An unconstrained numeric with no precision is also caught here.")
                .isEmpty();
    }

    @Test
    @DisplayName("the convention test is wired to a real schema, not silently querying nothing")
    void theSchemaIsActuallyVisible() {
        // Without this, both rules above would pass just as happily against an empty result set
        // caused by a wrong schema name — the failure mode of every "assert no offenders" test.
        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                """, String.class);

        assertThat(tables)
                .contains("audit_log", "setting", "attachment", "account", "account_group");
    }
}
