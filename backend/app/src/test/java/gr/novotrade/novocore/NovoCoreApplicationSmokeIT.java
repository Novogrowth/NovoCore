package gr.novotrade.novocore;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the whole stack starts: Spring context, connection to a real PostgreSQL, and Flyway.
 *
 * <p>Deliberately thin. Its value is that it fails when the wiring breaks — a bad migration,
 * an unresolvable property, a schema Hibernate refuses to validate — rather than that failure
 * surfacing later as a confusing error in an unrelated feature test.
 *
 * <p>Requires a running Docker daemon; see {@link PostgresTestContainerConfiguration}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // application.yml intentionally has no fallback for NOVOCORE_DB_PASSWORD, so that a
            // missing password stops the application rather than becoming a blank credential.
            // Overriding the property outright means that placeholder is never resolved here;
            // @ServiceConnection supplies the real connection details from the container.
            "spring.datasource.password=overridden-by-testcontainers",
            // Likewise there is no seeded user account, and the application refuses to start on
            // an empty user table without an initial owner. That refusal is the intended
            // behaviour — InitialOwnerBootstrapTest asserts it — so this test supplies one in
            // order to get past it and check what it is actually here to check.
            "novocore.bootstrap.owner-username=smoke.owner",
            "novocore.bootstrap.owner-password=smoke-owner-password",
        })
@Import(PostgresTestContainerConfiguration.class)
class NovoCoreApplicationSmokeIT {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("the application context loads against a real PostgreSQL")
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("Flyway ran and owns the schema")
    void flywayCreatedItsHistoryTable() throws SQLException {
        // There are no migrations yet (see core/src/main/resources/db/migration/README.md),
        // so this asserts that Flyway ran at all rather than checking any table of ours.
        // Once migrations exist, a failure here means Flyway did not execute — which with
        // ddl-auto: validate would otherwise show up as a puzzling entity mapping error.
        assertThat(tableExists("flyway_schema_history"))
                .as("Flyway did not run: its history table is absent. Hibernate is configured "
                        + "with ddl-auto: validate and will not create the schema itself.")
                .isTrue();
    }

    @Test
    @DisplayName("PostgreSQL is the database under test, not an in-memory substitute")
    void databaseIsPostgres() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            assertThat(productName)
                    .as("the ledger's invariants are enforced by PostgreSQL constraint "
                            + "triggers, so testing against anything else proves nothing "
                            + "about them")
                    .isEqualTo("PostgreSQL");
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = current_schema() AND table_name = '%s'
                )
                """.formatted(tableName);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }
}
