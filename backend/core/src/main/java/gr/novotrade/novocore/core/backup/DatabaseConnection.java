package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The database's host, port and name, taken from the JDBC URL the application already uses.
 *
 * <p>Parsed rather than configured a second time. {@code pg_dump} and {@code pg_restore} are
 * separate processes and need the connection as command-line arguments, and a second set of
 * settings for the same database is a thing that can disagree with the first — which would be
 * discovered as a backup faithfully dumping the wrong database, quite possibly an empty one.
 *
 * @param database the database name, also used to refuse a restore check pointed at it
 */
record DatabaseConnection(String host, int port, String database, String username,
        String password) {

    private static final String JDBC_PREFIX = "jdbc:postgresql://";
    private static final int DEFAULT_PORT = 5432;

    static DatabaseConnection parse(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX)) {
            throw new BackupNotConfiguredException(
                    "Cannot work out what to back up: the datasource URL '%s' is not a PostgreSQL "
                            .formatted(jdbcUrl) + "JDBC URL.");
        }

        // Reparsed as a URI after dropping the "jdbc:" scheme prefix, which URI cannot handle as
        // a nested scheme. Query parameters (?ssl=true&...) are deliberately dropped: they
        // configure the JDBC driver, and pg_dump takes its own.
        String withoutPrefix = jdbcUrl.substring("jdbc:".length());
        URI uri;
        try {
            uri = new URI(withoutPrefix);
        } catch (URISyntaxException e) {
            throw new BackupNotConfiguredException(
                    "Datasource URL '%s' cannot be parsed: %s".formatted(jdbcUrl, e.getMessage()));
        }

        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new BackupNotConfiguredException(
                    "Datasource URL '%s' names no database.".formatted(jdbcUrl));
        }

        return new DatabaseConnection(
                uri.getHost(),
                uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort(),
                path.substring(1),
                username,
                password);
    }

    /** The same connection pointed at a different database on the same server. */
    DatabaseConnection withDatabase(String other) {
        return new DatabaseConnection(host, port, other, username, password);
    }
}
