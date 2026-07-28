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
 * first {@code numeric} column in the schema. The {@code numeric(19,2)} half became live in step 5
 * with {@code product.selling_price}, the first <em>monetary</em> column: an account balance is the
 * sum of its journal lines and is never stored, so until a product had a price there was no amount
 * anywhere in the schema.
 *
 * <p>That first monetary column also settles what V1 left open — the naming of the {@code char(3)}
 * currency companion ADR 0005 requires. The convention is {@code <name>} and
 * {@code <name>_currency}, and {@link #everyMonetaryColumnCarriesItsCurrency()} now enforces it
 * across the whole schema, so step 7's monetary columns inherit the rule rather than re-deciding
 * it once there are dozens of them.
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
    @DisplayName("every monetary column has a char(3) currency column beside it")
    void everyMonetaryColumnCarriesItsCurrency() {
        // ADR 0005: EUR-only behaviour, but currency is modelled from day one, and every monetary
        // column carries one. Enforced structurally rather than by convention, because the failure
        // mode of a missing currency is a figure that looks right and means nothing once a second
        // currency exists — and by then there is history in the table.
        //
        // TWO KINDS OF MONETARY COLUMN, because scale alone cannot identify them. Every numeric(19,2)
        // is an amount and therefore money. A numeric(19,6) is a multiplier, and only SOME multipliers
        // are money: a VAT rate and a quantity are not, a unit cost is. So the discriminator for the
        // six-decimal case is the name, and `_cost` is the naming convention V12 established for it —
        // inventory_lot.unit_cost being the first.
        List<Map<String, Object>> offenders = jdbc.queryForList("""
                SELECT amount.table_name, amount.column_name
                FROM information_schema.columns amount
                LEFT JOIN information_schema.columns currency
                       ON currency.table_schema = amount.table_schema
                      AND currency.table_name   = amount.table_name
                      AND currency.column_name  = amount.column_name || '_currency'
                      AND currency.data_type IN ('character', 'character varying')
                      AND currency.character_maximum_length = 3
                WHERE amount.table_schema = current_schema()
                  AND amount.table_name <> ?
                  AND amount.data_type = 'numeric'
                  AND (amount.numeric_scale = 2
                       OR (amount.numeric_scale = 6 AND right(amount.column_name, 5) = '_cost'))
                  AND currency.column_name IS NULL
                ORDER BY amount.table_name, amount.column_name
                """, NOT_OURS);

        assertThat(offenders)
                .as("A numeric(19,2) column is a posted monetary amount, and a numeric(19,6) column "
                        + "whose name ends in _cost is a monetary multiplier. ADR 0005 requires "
                        + "every one to carry its currency in a companion char(3) column named "
                        + "<column>_currency — product.selling_price and "
                        + "inventory_lot.unit_cost being the first of each kind. Add the companion "
                        + "and a CHECK tying the two together, rather than exempting the column "
                        + "here: an amount whose currency is implied is an amount that means "
                        + "something different the day a second currency exists. A new monetary "
                        + "six-decimal column that is not a cost needs this rule widened, not "
                        + "sidestepped by naming it something else.")
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
                .contains("audit_log", "setting", "attachment", "account", "account_group",
                        "product", "customer", "supplier", "asset",
                        "inventory_lot", "serialized_unit", "bundle_component");

        // And the currency rule above is not passing vacuously either: there is a real
        // numeric(19,2) column in the schema for it to check.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND data_type = 'numeric' AND numeric_scale = 2
                """, Integer.class))
                .as("the monetary-column rule needs at least one monetary column to be meaningful")
                .isPositive();

        // Nor is its six-decimal half, which became live with inventory_lot.unit_cost in V12. Without
        // this, adding a second monetary multiplier with no currency would pass silently the day
        // somebody renamed unit_cost.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND data_type = 'numeric' AND numeric_scale = 6
                  AND right(column_name, 5) = '_cost'
                """, Integer.class))
                .as("the monetary-multiplier half of the rule needs at least one _cost column")
                .isPositive();
    }
}
