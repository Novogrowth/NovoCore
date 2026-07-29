package gr.novotrade.novocore.core.backup;

import com.zaxxer.hikari.HikariDataSource;
import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Works out what to tell {@code pg_dump} to connect to, from the connection pool the application
 * is actually using.
 *
 * <h2>Why the pool and not {@code spring.datasource.url}</h2>
 *
 * <p>Because that property is frequently not set. Under Testcontainers with
 * {@code @ServiceConnection} — which is how every integration test in this codebase runs — Boot
 * configures the {@code DataSource} programmatically from the container and never publishes a
 * {@code spring.datasource.url} at all, so a component injecting it fails to start the entire
 * context. That is not a test-only quirk to work around: it is the general case of the same
 * mistake, which is reading configuration that describes the database instead of asking the
 * database connection what it is.
 *
 * <p>Reading the pool is also the safer answer in production. A dump taken from whatever
 * {@code spring.datasource.url} happens to say could, if the two ever diverged, faithfully back up
 * a different database from the one the application is serving — and it would look completely
 * healthy while doing it.
 *
 * <p>The primer already records this trap from the other direction: a test cannot be pointed at a
 * non-Testcontainers database just by setting {@code spring.datasource.*}, because
 * {@code @ServiceConnection} overrides it. Same cause.
 */
@Component
class DatabaseConnectionProvider {

    private final DataSource dataSource;
    private final String fallbackUrl;
    private final String fallbackUsername;
    private final String fallbackPassword;

    DatabaseConnectionProvider(DataSource dataSource,
            @Value("${spring.datasource.url:}") String fallbackUrl,
            @Value("${spring.datasource.username:}") String fallbackUsername,
            @Value("${spring.datasource.password:}") String fallbackPassword) {
        this.dataSource = dataSource;
        this.fallbackUrl = fallbackUrl;
        this.fallbackUsername = fallbackUsername;
        this.fallbackPassword = fallbackPassword;
    }

    DatabaseConnection current() {
        // Hikari is Boot's pool and holds all three values, including the password, which JDBC
        // metadata deliberately does not expose. The properties remain as a fallback for a
        // deployment that replaces the pool.
        if (dataSource instanceof HikariDataSource hikari) {
            return DatabaseConnection.parse(
                    hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword());
        }
        if (fallbackUrl.isBlank()) {
            throw new BackupNotConfiguredException(
                    ("The datasource is a %s rather than a HikariDataSource and "
                            + "spring.datasource.url is not set, so there is no way to work out "
                            + "what pg_dump should connect to.")
                            .formatted(dataSource.getClass().getName()));
        }
        return DatabaseConnection.parse(fallbackUrl, fallbackUsername, fallbackPassword);
    }
}
