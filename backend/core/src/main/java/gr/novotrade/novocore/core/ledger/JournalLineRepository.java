package gr.novotrade.novocore.core.ledger;

import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through {@link gr.novotrade.novocore.core.api.ledger.JournalService}.
 *
 * <p><strong>Every aggregate here groups by side rather than netting in SQL.</strong> A
 * {@code sum(case when side = 'DEBIT' then amount else -amount end)} would be shorter and would throw
 * away the fact that an account with equal, large debits and credits is a different situation from one
 * with no activity at all — which is exactly what a trial balance has to show. It also keeps the sign
 * convention in one place, in Java, instead of once per query.
 *
 * <p>They also group by currency, deliberately: ADR 0005 never converts and never picks one, so two
 * currencies on one account come back as two rows and {@code Money.plus} refuses to combine them. That
 * failure is correct — a single figure would be meaningless.
 */
interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /**
     * One account's lines over a period, oldest first, with their entries fetched.
     *
     * <p>The entry is fetched because a ledger listing shows the date, source and description of the
     * entry each line came from; without it this is a query per line.
     */
    @Query("select l from JournalLine l join fetch l.entry e "
            + "where l.accountId = :accountId and e.entryDate between :from and :to "
            + "order by e.entryDate asc, e.id asc, l.lineNumber asc")
    List<JournalLine> findForAccountBetween(long accountId, LocalDate from, LocalDate to);

    /**
     * Every line referencing one sub-ledger entity, oldest first.
     *
     * <p>No date range: a sub-ledger ledger is read to reconcile it, and a reconciliation that skipped
     * the entries before an arbitrary start date would be answering a different question. Callers wanting
     * a period filter it.
     */
    @Query("select l from JournalLine l join fetch l.entry e "
            + "where l.subLedgerType = :subLedgerType and l.subLedgerId = :subLedgerId "
            + "order by e.entryDate asc, e.id asc, l.lineNumber asc")
    List<JournalLine> findForSubLedger(SubLedgerType subLedgerType, long subLedgerId);

    /** Debit and credit totals for one account, cumulative to {@code asOf}: side, currency, total. */
    @Query("select l.side, l.amountCurrency, sum(l.amount) from JournalLine l "
            + "where l.accountId = :accountId and l.entry.entryDate <= :asOf "
            + "group by l.side, l.amountCurrency")
    List<Object[]> sumBySideForAccountUpTo(long accountId, LocalDate asOf);

    /** The same, for every account with activity: account id, side, currency, total. */
    @Query("select l.accountId, l.side, l.amountCurrency, sum(l.amount) from JournalLine l "
            + "where l.entry.entryDate <= :asOf "
            + "group by l.accountId, l.side, l.amountCurrency")
    List<Object[]> sumBySideForAllAccountsUpTo(LocalDate asOf);

    /** Debit and credit totals for one sub-ledger entity: side, currency, total. */
    @Query("select l.side, l.amountCurrency, sum(l.amount) from JournalLine l "
            + "where l.subLedgerType = :subLedgerType and l.subLedgerId = :subLedgerId "
            + "and l.entry.entryDate <= :asOf "
            + "group by l.side, l.amountCurrency")
    List<Object[]> sumBySideForSubLedgerUpTo(
            SubLedgerType subLedgerType, long subLedgerId, LocalDate asOf);

    /**
     * VAT lines over a period: account id, VAT class id, side, currency, taxable base, VAT amount.
     *
     * <p>Grouped by side rather than netted, because a credit note debits Output VAT and a reversal
     * debits whatever was credited — the net is the reportable figure, and which direction an account is
     * depends on the account, not on the side. Grouped by VAT <em>class</em> rather than rate, because
     * classes {@code 1040} and {@code 1041} both charge 4% under different legal bases and different
     * myDATA codes, and collapsing them would report as one thing what AADE requires as two.
     */
    @Query("select l.accountId, l.vatClassId, l.side, l.amountCurrency, "
            + "sum(l.taxableBase), sum(l.amount) from JournalLine l "
            + "where l.vatClassId is not null and l.entry.entryDate between :from and :to "
            + "group by l.accountId, l.vatClassId, l.side, l.amountCurrency")
    List<Object[]> sumVatBetween(LocalDate from, LocalDate to);
}
