package gr.novotrade.novocore.core.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a real PostgreSQL instance to integration tests.
 *
 * <p>Import it into a test with {@code @Import(PostgresTestContainerConfiguration.class)}.
 * Spring owns the container's lifecycle as an ordinary bean, and {@code @ServiceConnection}
 * points the application's {@code DataSource} at it, so no test needs to set connection
 * properties by hand.
 *
 * <p><strong>Why not H2.</strong> NovoCore's most important guarantees are database
 * constraints, not Java code: debits must equal credits structurally
 * ({@code CLAUDE.md} rule 6), which is a deferred constraint trigger that an in-memory
 * database cannot execute. A test suite passing against H2 would say nothing about whether
 * those invariants actually hold, while looking like it did — and it would hold against code
 * paths going through the service layer only, not against a manual {@code psql} session.
 *
 * <p><strong>Requires a running Docker daemon.</strong> Tests importing this configuration
 * fail without one; that is deliberate rather than skipped, so an environment that cannot
 * verify the invariants reports it instead of passing quietly.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfiguration {

    /** Set by Surefire from the {@code postgres.docker.image} property in the parent POM. */
    static final String IMAGE_SYSTEM_PROPERTY = "novocore.postgres.image";

    /**
     * Used only when the tests are run outside Maven, such as from an IDE. Keep it in step
     * with {@code postgres.docker.image} in the parent POM and the image in
     * {@code docker/compose.yml}.
     */
    static final String FALLBACK_IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer novocorePostgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(resolveImage()));
    }

    private static String resolveImage() {
        String configured = System.getProperty(IMAGE_SYSTEM_PROPERTY);
        return configured == null || configured.isBlank() ? FALLBACK_IMAGE : configured;
    }
}
