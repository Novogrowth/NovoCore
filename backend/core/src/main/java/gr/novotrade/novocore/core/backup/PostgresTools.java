package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs {@code pg_dump}, {@code pg_restore} and {@code psql}.
 *
 * <p>External binaries rather than a pure-Java dump, and the reason is the restore rather than the
 * dump. Anything hand-rolled would have to be trusted to reproduce PostgreSQL's own logical format
 * exactly — every extension, every collation, every future feature a migration introduces — and
 * the way that fails is a backup that restores <em>almost</em> correctly. {@code pg_dump}'s output
 * is the format {@code pg_restore} is guaranteed to read, and the guarantee is the point.
 *
 * <p>Custom format ({@code -Fc}), not plain SQL: it is compressed, and {@code pg_restore} can list
 * and selectively restore from it, which is what makes a partial recovery possible rather than
 * all-or-nothing.
 *
 * <h2>The password never appears in a command line</h2>
 *
 * <p>It goes in the child process's environment as {@code PGPASSWORD}. Passed as an argument it
 * would be visible to every process on the host through {@code /proc}, to {@code ps}, and in any
 * crash dump — for the lifetime of the dump, which on a real database is minutes.
 *
 * <h2>stderr is drained, not left to fill</h2>
 *
 * <p>{@code pg_dump} writes progress and warnings to stderr while writing the dump to stdout. If
 * nothing reads stderr, its pipe buffer fills and the process blocks forever with the backup
 * half-written and no error anywhere. A virtual thread drains it, and what it collected becomes
 * the failure message when the exit code is non-zero — which is the difference between "pg_dump
 * failed" and "pg_dump failed: FATAL: password authentication failed".
 */
@Component
public class PostgresTools {

    private static final Logger log = LoggerFactory.getLogger(PostgresTools.class);

    private final String pgDump;
    private final String pgRestore;
    private final String psql;

    PostgresTools(
            @Value("${novocore.backup.pg-dump-path:pg_dump}") String pgDump,
            @Value("${novocore.backup.pg-restore-path:pg_restore}") String pgRestore,
            @Value("${novocore.backup.psql-path:psql}") String psql) {
        this.pgDump = pgDump;
        this.pgRestore = pgRestore;
        this.psql = psql;
    }

    /**
     * Starts a dump and hands back its stdout for the caller to encrypt as it arrives.
     *
     * <p>Streaming rather than dump-to-file-then-encrypt, so the unencrypted database never exists
     * as a file. A temp file would be readable by anything else on the host for as long as the
     * encryption took, and would be left behind by a crash at exactly the wrong moment.
     */
    RunningDump startDump(DatabaseConnection connection) throws IOException {
        List<String> command = new ArrayList<>(List.of(
                pgDump,
                "--host=" + connection.host(),
                "--port=" + connection.port(),
                "--username=" + connection.username(),
                "--dbname=" + connection.database(),
                "--format=custom",
                // Ownership and grants belong to the environment being restored INTO. A restore
                // onto a fresh host would otherwise fail on roles that do not exist there yet,
                // which is precisely the situation a backup is for.
                "--no-owner",
                "--no-privileges",
                "--no-password"));

        Process process = start(command, connection);
        return new RunningDump(process, drainStderr(process, "pg_dump"));
    }

    /**
     * Restores a custom-format dump, read from {@code artefact}, into {@code connection}'s
     * database.
     *
     * @return pg_restore's stderr, which is where it reports per-object problems
     */
    String restore(DatabaseConnection connection, Path artefact) {
        return run(List.of(
                pgRestore,
                "--host=" + connection.host(),
                "--port=" + connection.port(),
                "--username=" + connection.username(),
                "--dbname=" + connection.database(),
                "--no-owner",
                "--no-privileges",
                "--no-password",
                // Single transaction, so a restore either lands whole or not at all. A partially
                // restored scratch database would pass some assertions and fail others, and the
                // report would describe a state that never existed as a real database.
                "--single-transaction",
                artefact.toString()), connection, "pg_restore");
    }

    /** Runs one statement with {@code psql}, for the CREATE/DROP DATABASE a restore check needs. */
    String runStatement(DatabaseConnection connection, String sql) {
        return run(List.of(
                psql,
                "--host=" + connection.host(),
                "--port=" + connection.port(),
                "--username=" + connection.username(),
                "--dbname=" + connection.database(),
                "--no-password",
                // Without this psql exits 0 having printed the error, and a failed CREATE DATABASE
                // would be reported as a successful one.
                "--set=ON_ERROR_STOP=1",
                "--quiet",
                "--no-align",
                "--tuples-only",
                "--command=" + sql), connection, "psql");
    }

    /** The version string, used to report configuration and to fail early if the binary is absent. */
    public Optional<String> pgDumpVersion() {
        try {
            Process process = new ProcessBuilder(List.of(pgDump, "--version"))
                    .redirectErrorStream(true)
                    .start();
            String output = readFully(process.getInputStream());
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return process.exitValue() == 0 ? Optional.of(output.trim()) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    void requireAvailable() {
        if (pgDumpVersion().isEmpty()) {
            throw new BackupNotConfiguredException(
                    ("'%s' is not runnable, so no backup can be taken. The runtime image must "
                            + "carry a postgresql-client whose major version matches the server's "
                            + "— an older pg_dump refuses to dump a newer server outright.")
                            .formatted(pgDump));
        }
    }

    private String run(List<String> command, DatabaseConnection connection, String what) {
        try {
            Process process = start(command, connection);
            String stderr = drainStderr(process, what).get();
            String stdout = readFully(process.getInputStream());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(
                        "%s exited %d: %s".formatted(what, exit, firstLines(stderr)));
            }
            return stdout.isBlank() ? stderr : stdout;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(what + " was interrupted", e);
        }
    }

    private Process start(List<String> command, DatabaseConnection connection) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        // The environment, never the command line — see the class comment.
        builder.environment().put("PGPASSWORD", connection.password());
        // Deterministic messages regardless of the host's locale, so the error text a failure
        // records is one a later reader can search for.
        builder.environment().put("LC_ALL", "C");
        return builder.start();
    }

    /**
     * Consumes stderr on its own thread so the child cannot block on a full pipe, and keeps what
     * it read for the error message.
     */
    private static StderrDrain drainStderr(Process process, String what) {
        StringBuilder collected = new StringBuilder();
        Thread thread = Thread.ofVirtual().name("stderr-" + what).start(() -> {
            try (InputStream stderr = process.getErrorStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stderr.read(buffer)) != -1) {
                    synchronized (collected) {
                        collected.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                log.debug("Stopped reading {} stderr: {}", what, e.getMessage());
            }
        });
        return new StderrDrain(thread, collected);
    }

    private static String readFully(InputStream stream) throws IOException {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The first few lines of a tool's stderr. PostgreSQL's client tools report the actual problem
     * first and then one line per dependent failure, so the tail is noise and the head is the
     * answer.
     */
    static String firstLines(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "(no output)";
        }
        String[] lines = stderr.strip().split("\\R");
        return String.join(" | ", List.of(lines).subList(0, Math.min(lines.length, 4)));
    }

    /** A dump in progress: its stdout is the plaintext to encrypt. */
    record RunningDump(Process process, StderrDrain stderr) {

        InputStream output() {
            return process.getInputStream();
        }

        /** @throws IllegalStateException if pg_dump reported a failure, naming what it said */
        void awaitSuccess() throws InterruptedException {
            String message = stderr.get();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(
                        "pg_dump exited %d: %s".formatted(exit, firstLines(message)));
            }
        }
    }

    /** The collected stderr, joined when it is needed. */
    record StderrDrain(Thread thread, StringBuilder collected) {

        String get() throws InterruptedException {
            thread.join(TimeUnit.SECONDS.toMillis(30));
            synchronized (collected) {
                return collected.toString();
            }
        }
    }
}
