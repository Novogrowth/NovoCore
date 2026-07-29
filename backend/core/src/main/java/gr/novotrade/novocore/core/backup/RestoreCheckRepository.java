package gr.novotrade.novocore.core.backup;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestoreCheckRepository extends JpaRepository<RestoreCheck, Long> {

    List<RestoreCheck> findByBackupRunIdOrderByStartedAtDescIdDesc(long backupRunId);

    /** "When did we last prove a restore works?" — the question brief §13 exists to make answerable. */
    List<RestoreCheck> findByOrderByStartedAtDescIdDesc(Limit limit);

    default Optional<RestoreCheck> findLatest() {
        return findByOrderByStartedAtDescIdDesc(Limit.of(1)).stream().findFirst();
    }
}
