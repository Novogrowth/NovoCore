package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The journal: posting entries, correcting them, and the balances computed from them.
 *
 * <p>Brief §6's <strong>lower</strong> layer. Typed transactions — Purchase Invoice, Sales Invoice, Credit
 * Note, Receipt, Payment, Bank Transfer, Manual Journal Entry — are the upper layer, and every one of them
 * arrives at the ledger through {@link #post}. Only the Manual Journal Entry has no upper layer of its own,
 * which is why {@link #postManualEntry} exists.
 *
 * <p><strong>Debits equal credits, structurally.</strong> {@code CLAUDE.md} rule 6 is said three times on
 * purpose: {@link NewJournalEntry#balances()} lets a caller check before trying, this service throws
 * {@link UnbalancedJournalEntryException} naming both totals, and a <em>deferred constraint trigger</em>
 * refuses the transaction at commit. The trigger is the guarantee — it is what holds against a manual
 * {@code psql} session, and it is deferred because an entry is legitimately unbalanced halfway through
 * being written.
 *
 * <p><strong>Nothing is ever deleted.</strong> There is no delete method here and the database refuses one
 * by trigger, in either table. With no period locking (brief §6) there is no point at which the ledger is
 * finished with, so the only two ways to change a posted entry are the two Q13 names: amend it in place if
 * its source allows that, or reverse it.
 *
 * <p><strong>No account balance is stored anywhere.</strong> Step 3 gave {@code account} no balance column
 * deliberately; {@link #balanceOf} sums the lines on every read. That is also why there is no
 * "recalculate balances" operation — there is nothing to recalculate.
 *
 * <p><strong>Permissions.</strong> Everything here belongs to {@link Section#JOURNAL}. As in step 4b and
 * step 6, the section is stated here and checked at the controller, because the core's own posting rules
 * call these methods with no user in front of them. There is no controller yet.
 */
public interface JournalService {

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    /**
     * Posts an entry.
     *
     * <p>The entry point for every typed transaction, including a reversal posted by the service that owns
     * the state behind it (set {@code reversalOfEntryId} on the request). Validation, in the order a caller
     * will hit it:
     *
     * <ul>
     *   <li>every line's amount is in the same currency — an entry spanning two would have a meaningless
     *       total, and ADR 0005 never converts;
     *   <li>debits equal credits;
     *   <li>every account exists, and is active unless this entry is a reversal — a posted entry has to
     *       stay correctable after the account it used has been retired, which is the whole point of
     *       deactivate-rather-than-delete;
     *   <li>a line on a {@code CONTROL} account carries a sub-ledger reference of that account's declared
     *       type (brief §6);
     *   <li>a {@link VatDimension} appears only on a line posting to a VAT account (Q14);
     *   <li>a reversal's lines are the exact mirror of the original's, and the original has not already
     *       been reversed.
     * </ul>
     *
     * @throws UnbalancedJournalEntryException if debits do not equal credits
     * @throws InvalidJournalEntryException for any of the other conditions above
     * @throws JournalEntryNotFoundException if {@code reversalOfEntryId} names no entry
     * @throws gr.novotrade.novocore.core.api.account.AccountNotFoundException if a line names no account
     */
    JournalEntryView post(NewJournalEntry request);

    /**
     * Posts a Manual Journal Entry — a raw entry somebody wrote by hand.
     *
     * <p>Takes no source, and that is the point: this is the shape a request from a person can be turned
     * into, so a manual-entry endpoint cannot post an entry claiming to be a sales invoice. The typed
     * transactions use {@link #post} from inside the core.
     */
    JournalEntryView postManualEntry(
            LocalDate entryDate, String description, List<NewJournalLine> lines);

    // ---------------------------------------------------------------------------------------
    // Correcting — Q13
    // ---------------------------------------------------------------------------------------

    /**
     * Replaces a posted entry's date, description and lines in place. <strong>Q13's editable half.</strong>
     *
     * <p>Permitted for a Receipt, Payment, Bank Transfer or Manual Journal Entry; refused for a Purchase
     * Invoice, Sales Invoice, Credit Note or inventory write-off. The previous date, description and lines
     * are written to the audit log first, which is what makes "editable in place" leave a trail rather
     * than overwrite one — Q13 names that mechanism explicitly.
     *
     * <p><strong>Replaces the whole line list, never merges.</strong> A partial change would leave the
     * remainder in a state nobody chose, the same argument that makes a chart-of-accounts reorder name
     * every member and {@code BundleService.define} replace the whole component list.
     *
     * <p>Refused on an entry that is a reversal or has been reversed: a reversal's lines are defined as
     * the mirror of another entry's, so editing either half leaves a pair that no longer nets to zero
     * while still claiming to.
     *
     * @throws JournalEntryNotAmendableException if the source is immutable, or the entry is a reversal or
     *     has been reversed
     * @throws UnbalancedJournalEntryException if the replacement lines do not balance
     * @throws InvalidJournalEntryException for the same conditions {@link #post} checks
     */
    JournalEntryView amend(
            long entryId, LocalDate entryDate, String description, List<NewJournalLine> lines);

    /**
     * Posts the mirror of an entry. <strong>Q13's reversal half.</strong>
     *
     * <p>The reversing entry carries the same source as the original — a reversal of a sales invoice is
     * still sales-invoice-sourced — so it inherits the same correction policy, and it points at the
     * original through {@code reversalOfEntryId}. An entry can be reversed at most once, by UNIQUE
     * constraint.
     *
     * <p><strong>Refused when the source owns state the ledger cannot see</strong> — a lot's remaining
     * quantity, a receipt's allocations, a serialized unit's status. Reversing the money alone would leave
     * that state stranded, so those sources are reversed by the service that owns them, which undoes its
     * own state and posts the mirror through {@link #post}. The refusal names the service to use. See
     * {@link JournalSource#isReversibleThroughTheLedgerAlone()}.
     *
     * @param reversalDate the accounting date of the reversal. Usually today rather than the original's
     *     date: a reversal is a new fact, and backdating it into a period already reported on hides that
     *     anything happened. Backdating is still permitted — there is no period locking.
     * @throws InvalidJournalEntryException if the source is not reversible through the ledger alone, or
     *     the entry has already been reversed
     * @throws JournalEntryNotFoundException if there is no such entry
     */
    JournalEntryView reverse(long entryId, LocalDate reversalDate, String description);

    /**
     * The lines that would reverse an entry: same accounts, same amounts, same references, opposite sides.
     *
     * <p>For a service reversing its own document — it undoes its state, asks for these, and posts them
     * with {@code reversalOfEntryId} set. Exists so that mirroring is written once and cannot drift from
     * what {@link #post} validates a reversal against.
     *
     * @throws JournalEntryNotFoundException if there is no such entry
     */
    List<NewJournalLine> mirrorOf(long entryId);

    // ---------------------------------------------------------------------------------------
    // Reading entries
    // ---------------------------------------------------------------------------------------

    Optional<JournalEntryView> findEntry(long entryId);

    /** @throws JournalEntryNotFoundException if absent */
    JournalEntryView requireEntry(long entryId);

    /**
     * Several entries at once, by id, oldest first.
     *
     * <p>Present so that a caller holding a list of records each pointing at an entry — the stock
     * write-offs, and later the invoices and receipts — reads them in one query instead of one per row.
     * Ids that do not exist are absent from the result rather than throwing: the caller already knows
     * which it asked for, and a partial answer is more useful than none when the list is a report.
     */
    List<JournalEntryView> findEntries(Collection<Long> entryIds);

    /**
     * Entries with an accounting date in the range, both ends inclusive, oldest first then by id.
     *
     * <p>By {@code entryDate}, not {@code createdAt}: a backdated entry belongs in the period it is dated
     * to, which is the whole reason the two are separate columns.
     */
    List<JournalEntryView> entriesBetween(LocalDate from, LocalDate to);

    /**
     * A page of entries, <strong>without their lines</strong>, narrowed by {@code filter}.
     *
     * <p>The journal is the one table here genuinely expected to grow unbounded over the years the
     * business runs — unlike users, roles or settings, which are small and bounded — so this is paged
     * from the start rather than added to the list of unpaged reads needing retrofit later.
     *
     * <p><strong>Returns {@link JournalEntrySummaryView}, and that is not a convenience.</strong>
     * {@link JournalEntryView} refuses to exist without its lines, and a listing carrying every line
     * of every entry would be a different thing entirely. See that type before changing this.
     *
     * <p>The natural order is {@code entryDate} ascending with the id breaking ties. A screen wanting
     * newest-first asks for {@link JournalEntrySort#ENTRY_DATE} descending.
     *
     * @throws gr.novotrade.novocore.core.api.account.AccountNotFoundException if the filter names an
     *     account that does not exist — refused rather than answered with an empty page, which would
     *     read as "this account has no entries"
     */
    PageResponse<JournalEntrySummaryView> pageOfEntries(
            JournalEntryFilter filter, PageRequest pageRequest);

    /**
     * A page of one account's lines — the account ledger, paged.
     *
     * <p>The paged counterpart to {@link #linesOf}, which stays for the core's own callers. One bank
     * account over a year is every transaction that touched it, so this is the second genuinely
     * unbounded read in the ledger and the one a drill-down from an entry listing lands on.
     *
     * <p>Still returns lines rather than a ledger type carrying a running balance, for
     * {@link #linesOf}'s reason — and paging makes that argument stronger rather than weaker, since a
     * running balance computed within a page would silently restart at each page boundary. The
     * opening figure is {@link #balanceOf} at the day before {@code from}.
     *
     * @param from earliest {@code entryDate}, inclusive; null for no lower bound
     * @param to latest {@code entryDate}, inclusive; null for no upper bound
     * @throws gr.novotrade.novocore.core.api.account.AccountNotFoundException if there is no such
     *     account
     */
    PageResponse<JournalLineView> pageOfLines(
            long accountId, LocalDate from, LocalDate to, PageRequest pageRequest);

    /**
     * One account's lines over a period, oldest first — the account ledger.
     *
     * <p>Returns lines rather than a purpose-built ledger type carrying a running balance, because a
     * running balance is presentation: it depends on where the reader started, and it is meaningless
     * against a filtered list. The opening figure is {@link #balanceOf} at the day before {@code from},
     * and the report that puts the two together is phase 8's.
     */
    List<JournalLineView> linesOf(long accountId, LocalDate from, LocalDate to);

    /**
     * Every line referencing one sub-ledger entity, oldest first — one customer's ledger, one lot's
     * history, one asset's cost and depreciation.
     *
     * <p><strong>This is what makes a Control account reconcilable</strong> rather than merely declared to
     * be: the control account's balance is the sum of its lines, and the sum of these per entity has to
     * come to the same figure by construction, because they are the same rows read two ways.
     */
    List<JournalLineView> linesFor(SubLedgerRef subLedgerRef);

    // ---------------------------------------------------------------------------------------
    // Balances
    // ---------------------------------------------------------------------------------------

    /**
     * What an account's lines add up to, cumulatively, up to and including {@code asOf}.
     *
     * <p>Computed on every read; nothing is stored. Returns a balance with no activity rather than
     * throwing when the account has never been posted to — that is a real answer, and
     * {@link AccountBalance#hasNoActivity()} distinguishes it from a balance of zero.
     *
     * @throws gr.novotrade.novocore.core.api.account.AccountNotFoundException if there is no such account
     */
    AccountBalance balanceOf(long accountId, LocalDate asOf);

    /**
     * The balance of the account carrying a system key — for a posting rule or a check that needs a
     * specific account rather than one a human chose.
     *
     * @throws gr.novotrade.novocore.core.api.account.AccountNotFoundException if no account carries the
     *     key, which is a broken chart rather than a missing option
     */
    AccountBalance balanceOf(AccountSystemKey systemKey, LocalDate asOf);

    /**
     * The net position of one sub-ledger entity, debit-positive — what one customer owes, what one lot is
     * carried at.
     *
     * <p>Debit-positive rather than presented on a normal side, because a sub-ledger reference can appear
     * on lines of accounts with opposite normal sides: an asset's cost and its accumulated depreciation
     * both carry the same {@code ASSET} reference, and their net is the carrying value only if the signs
     * are not flipped per account first.
     */
    Money subLedgerBalanceOf(SubLedgerRef subLedgerRef, LocalDate asOf);

    /**
     * Every account with activity, in chart order, with its debit and credit totals.
     *
     * <p>{@link TrialBalance#isBalanced()} is the whole-ledger restatement of {@code CLAUDE.md} rule 6.
     */
    TrialBalance trialBalance(LocalDate asOf);

    // ---------------------------------------------------------------------------------------
    // VAT — Q14
    // ---------------------------------------------------------------------------------------

    /**
     * Output and input VAT over a period, grouped by direction and VAT class.
     *
     * <p>Read off the {@link VatDimension} that Q14 puts on every VAT line, which is what stops that
     * column being one nothing consumes. Not a report: this is the query phase 8's VAT report and phase
     * 7's myDATA adapter both need, and it is here because the column it reads is defined here.
     *
     * <p><strong>Output and input come back separately and are never netted</strong> (Q14). Netting them
     * would collapse the two figures a VAT return is made of and would make a refund position
     * indistinguishable from a small liability.
     *
     * <p>Reverse-charge entries appear on <em>both</em> sides for the same class and base, which is exactly
     * what a self-assessed intra-EU acquisition is: output VAT charged to ourselves and input VAT
     * reclaimed, netting to zero in cash and to nothing in the return's bottom line while still being two
     * real figures that have to be declared.
     */
    List<VatTotal> vatTotals(LocalDate from, LocalDate to);
}
