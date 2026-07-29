package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.RestoreCheckStatus;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * One attempt to prove a backup restores.
 *
 * <p>{@code findings} is kept on a passing check as well as a failing one, and stored as text
 * rather than a boolean. "The restore worked" is the claim this system has never been able to make
 * (brief §13); a green flag with nothing behind it would be the same unverified claim wearing a
 * tick. What is stored instead is what was actually asserted — the schema version that came back,
 * the row counts, and whether the restored ledger balances.
 */
@Entity
@Table(name = "restore_check")
class RestoreCheck extends AuditableEntity {

    /** Findings are joined into one column with this separator; they are prose, not a query target. */
    private static final String SEPARATOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backup_run_id", nullable = false)
    private BackupRun backupRun;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestoreCheckStatus status;

    @Column
    private String findings;

    @Column
    private String error;

    protected RestoreCheck() {
    }

    RestoreCheck(BackupRun backupRun, Instant startedAt) {
        this.backupRun = backupRun;
        this.startedAt = startedAt;
        this.status = RestoreCheckStatus.RUNNING;
    }

    void passed(Instant finishedAt, List<String> findings) {
        this.status = RestoreCheckStatus.PASSED;
        this.finishedAt = finishedAt;
        this.findings = String.join(SEPARATOR, findings);
        this.error = null;
    }

    void failed(Instant finishedAt, List<String> findings, String error) {
        this.status = RestoreCheckStatus.FAILED;
        this.finishedAt = finishedAt;
        // Kept on failure too: the assertions that DID pass narrow down what went wrong, and
        // discarding them would leave only the one line that says something is broken.
        this.findings = String.join(SEPARATOR, findings);
        this.error = truncate(error);
    }

    Long getId() {
        return id;
    }

    RestoreCheckStatus getStatus() {
        return status;
    }

    RestoreCheckView toView() {
        return new RestoreCheckView(
                id,
                backupRun.getId(),
                backupRun.getArtefactName(),
                startedAt,
                finishedAt,
                status,
                findings == null || findings.isBlank() ? List.of()
                        : List.of(findings.split(SEPARATOR)),
                error);
    }

    private static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "(no message)";
        }
        return error.length() <= 4000 ? error : error.substring(0, 4000) + "…";
    }
}
