package gr.novotrade.novocore.core;

import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import gr.novotrade.novocoretest.CoreTestApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base for core integration tests: a real PostgreSQL, the real Flyway migrations, and
 * Hibernate running with {@code ddl-auto: validate}.
 *
 * <p>That last part does more work than it looks. Because Hibernate validates every entity
 * mapping against the schema Flyway actually produced, any of these tests starting at all
 * proves the two agree — a column renamed in a migration but not in its entity fails here
 * rather than at runtime on the one code path that touches it.
 *
 * <p>Deliberately not {@code @Transactional}. Rolling each test back would be convenient, but
 * the audit log is written in its own transaction precisely so it survives the rollback of the
 * operation it records, and a transactional test would hide whether that works. Tests here use
 * distinct keys instead of relying on a clean database.
 */
@SpringBootTest(
        classes = CoreTestApplication.class,
        // application.yml has no fallback for NOVOCORE_DB_PASSWORD by design; @ServiceConnection
        // supplies the real credentials from the container.
        properties = "spring.datasource.password=overridden-by-testcontainers")
@Import(PostgresTestContainerConfiguration.class)
public abstract class AbstractCoreIntegrationTest {
}
