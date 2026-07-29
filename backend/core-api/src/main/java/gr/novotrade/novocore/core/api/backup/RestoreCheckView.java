package gr.novotrade.novocore.core.api.backup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One restore verification: an artefact decrypted, restored into a scratch database, and asserted
 * against.
 *
 * <p>{@link #findings} is deliberately kept on a passing check too, not only a failing one. A
 * green flag with nothing behind it is what "we have backups" already means before anybody tests
 * one; the point of this record is to be able to say <em>what</em> was verified — which schema
 * version came back, how many accounts and journal lines, and whether the restored ledger still
 * balances.
 *
 * @param findings one line per assertion, in the order they ran
 */
public record RestoreCheckView(
        long id,
        long backupRunId,
        String artefactName,
        Instant startedAt,
        Instant finishedAt,
        RestoreCheckStatus status,
        List<String> findings,
        String error) {

    public RestoreCheckView {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean passed() {
        return status == RestoreCheckStatus.PASSED;
    }

    public Optional<String> errorIfAny() {
        return Optional.ofNullable(error);
    }
}
