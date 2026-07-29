package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface BackupRunRepository extends JpaRepository<BackupRun, Long> {

    List<BackupRun> findByOrderByStartedAtDescIdDesc(Limit limit);

    Optional<BackupRun> findFirstByStatusOrderByStartedAtDescIdDesc(BackupRunStatus status);

    Optional<BackupRun> findByArtefactName(String artefactName);

    /**
     * Every successful backup whose artefact still exists — the input to the retention rule.
     *
     * <p>Successful only, and this restriction is load-bearing rather than tidy: a failed run has
     * no artefact to keep or delete, and letting failures count towards the rolling seven would
     * mean a week of failures silently evicting the last good backups. The one week you would most
     * want them.
     */
    List<BackupRun> findByStatusAndPrunedAtIsNull(BackupRunStatus status);
}
