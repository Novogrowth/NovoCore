package gr.novotrade.novocore.core.banking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.banking.BankTransferService}.
 */
interface BankTransferRepository extends JpaRepository<BankTransfer, Long> {

    List<BankTransfer> findByTransferDateBetweenOrderByTransferDateAscIdAsc(
            LocalDate from, LocalDate to);

    Optional<BankTransfer> findByJournalEntryId(long journalEntryId);

    /** Either side. A transfer touching an account appears once, whichever end it is. */
    @Query("""
            SELECT transfer FROM BankTransfer transfer
            WHERE transfer.fromAccountId = :accountId OR transfer.toAccountId = :accountId
            ORDER BY transfer.transferDate ASC, transfer.id ASC
            """)
    List<BankTransfer> findInvolving(long accountId);
}
