package gr.novotrade.novocore.core.ledger;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.ledger.AccountBalance;
import gr.novotrade.novocore.core.api.ledger.InvalidJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotAmendableException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryFilter;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotFoundException;
import gr.novotrade.novocore.core.api.ledger.JournalEntrySort;
import gr.novotrade.novocore.core.api.ledger.JournalEntrySummaryView;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.ledger.TrialBalance;
import gr.novotrade.novocore.core.api.ledger.UnbalancedJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.VatDimension;
import gr.novotrade.novocore.core.api.ledger.VatDirection;
import gr.novotrade.novocore.core.api.ledger.VatTotal;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.support.SpringPaging;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The journal: posting, correcting, and the balances computed from the lines.
 *
 * <p><strong>Three things this class deliberately does not do.</strong>
 *
 * <ul>
 *   <li><strong>It does not own the balance invariant.</strong> {@code CLAUDE.md} rule 6 is a deferred
 *       constraint trigger in V15. The check here exists only to produce
 *       {@link UnbalancedJournalEntryException} with both totals in it, because a driver-level message
 *       naming a trigger is not something a person can act on. If this check were ever deleted the
 *       invariant would still hold; if the trigger were deleted it would not, and a test asserts the
 *       trigger fires.
 *   <li><strong>It does not validate that a sub-ledger id exists.</strong> That is also a trigger, and
 *       not by preference: checking a lot id here would mean depending on the inventory service, which
 *       already depends on this one in order to post a write-off. A bean cycle for a check the database
 *       can make directly is a poor trade.
 *   <li><strong>It does not store or cache a balance.</strong> Step 3 gave {@code account} no balance
 *       column; every read here sums the lines.
 * </ul>
 *
 * <p><strong>Account names are resolved once per read.</strong> {@code Account} is package-private in its
 * own slice, so a line holds a plain {@code accountId} and a projection needs
 * {@link ChartOfAccountsService} to name it. Rather than one lookup per line, each read builds a map of
 * the whole chart — 67 rows — and indexes into it. That is one extra query per read regardless of how
 * many lines come back, which is the shape that stays correct as the ledger grows.
 */
@Service
class JournalServiceImpl implements JournalService {

    private static final String ENTRY_ENTITY = "JournalEntry";

    /**
     * The currency a balance is reported in when there are no lines to take one from.
     *
     * <p>ADR 0005: EUR-only behaviour, currency modelled from day one. A zero balance still needs a
     * currency to be a {@code Money}, and this is the only place in the ledger that has to name one
     * without reading it off a line.
     */
    private static final Currency REPORTING_CURRENCY = Money.EUR;

    /**
     * The orderings a journal listing offers, mapped to entity properties.
     *
     * <p>{@code SpringPaging} refuses anything not in this map, which is the check that holds if this
     * service is ever called from somewhere that did not bind the parameter to
     * {@link JournalEntrySort} first. The routes do bind it; this is the belt to that pair of braces,
     * and it is why nothing a caller supplies can ever reach a query.
     */
    private static final Map<String, String> ENTRY_SORTS = Map.of(
            JournalEntrySort.ENTRY_DATE.name(), "entryDate",
            JournalEntrySort.RECORDED_AT.name(), "createdAt",
            JournalEntrySort.SOURCE.name(), "source");

    /**
     * The account ledger's orderings.
     *
     * <p>Deliberately only the entry's own date: a line has no independent date, and offering
     * {@code lineNumber} as a top-level sort would order the whole account by a number that is only
     * meaningful within one entry.
     */
    private static final Map<String, String> LINE_SORTS = Map.of(
            JournalEntrySort.ENTRY_DATE.name(), "entry.entryDate",
            JournalEntrySort.RECORDED_AT.name(), "entry.createdAt",
            JournalEntrySort.SOURCE.name(), "entry.source");

    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final ChartOfAccountsService chartOfAccounts;
    private final VatClassService vatClasses;
    private final AuditLogService auditLog;

    JournalServiceImpl(JournalEntryRepository entries, JournalLineRepository lines,
            ChartOfAccountsService chartOfAccounts, VatClassService vatClasses,
            AuditLogService auditLog) {
        this.entries = entries;
        this.lines = lines;
        this.chartOfAccounts = chartOfAccounts;
        this.vatClasses = vatClasses;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public JournalEntryView post(NewJournalEntry request) {
        Objects.requireNonNull(request, "request");

        requireOneCurrency(request.lines());
        requireBalanced(request);
        validateLinesAgainstTheChart(request.lines(), request.isReversal());

        if (request.isReversal()) {
            requireExactMirror(request.reversalOfEntryId(), request.lines());
        }

        JournalEntry entry = new JournalEntry(
                request.entryDate(), request.description(), request.source(),
                request.reversalOfEntryId());
        for (NewJournalLine line : request.lines()) {
            entry.addLine(new JournalLine(line.accountId(), line.side(), line.amount(),
                    line.description(), line.subLedgerRef(), line.vat()));
        }
        JournalEntry saved = entries.save(entry);

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("source", request.source().name());
        detail.put("entryDate", request.entryDate().toString());
        detail.put("total", request.totalDebits().toString());
        detail.put("lines", String.valueOf(request.lines().size()));
        if (request.isReversal()) {
            detail.put("reversalOf", String.valueOf(request.reversalOfEntryId()));
        }
        auditLog.record("journal-entry.posted", ENTRY_ENTITY, String.valueOf(saved.getId()), detail);

        return toView(saved, accountsById());
    }

    @Override
    @Transactional
    public JournalEntryView postManualEntry(
            LocalDate entryDate, String description, List<NewJournalLine> entryLines) {
        return post(NewJournalEntry.of(
                entryDate, description, JournalSource.MANUAL_JOURNAL_ENTRY, entryLines));
    }

    // ---------------------------------------------------------------------------------------
    // Correcting — Q13
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public JournalEntryView amend(long entryId, LocalDate entryDate, String description,
            List<NewJournalLine> replacementLines) {
        Objects.requireNonNull(entryDate, "entryDate");
        Objects.requireNonNull(replacementLines, "lines");

        JournalEntry entry = loadWithLines(entryId);

        if (!entry.getSource().isAmendable()) {
            throw new JournalEntryNotAmendableException(entryId, entry.getSource());
        }
        if (entry.isReversal()) {
            throw new JournalEntryNotAmendableException(entryId,
                    "it is the reversal of entry " + entry.getReversalOfId() + ". A reversal's lines "
                            + "are defined as the mirror of another entry's, so editing them would "
                            + "leave a pair that no longer nets to zero while still claiming to. "
                            + "Post a further correcting entry instead.");
        }
        entries.findByReversalOfId(entryId).ifPresent(reversal -> {
            throw new JournalEntryNotAmendableException(entryId,
                    "it has already been reversed by entry " + reversal.getId() + ". Editing it now "
                            + "would change what that reversal is the mirror of. Post a new entry "
                            + "with the correct figures.");
        });

        // Validated as a whole before anything is touched, so a rejected amendment leaves the entry
        // exactly as it was rather than half-replaced.
        NewJournalEntry asRequest = new NewJournalEntry(
                entryDate, description, entry.getSource(), null, replacementLines);
        requireOneCurrency(replacementLines);
        requireBalanced(asRequest);
        validateLinesAgainstTheChart(replacementLines, false);

        // Q13 names the audit log as the mechanism that makes "editable in place" acceptable, so the
        // previous state is recorded BEFORE it is overwritten. Without this the edit is indistinguishable
        // from the entry having always said the new thing.
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("source", entry.getSource().name());
        detail.put("previousEntryDate", entry.getEntryDate().toString());
        detail.put("previousDescription", entry.getDescription());
        detail.put("previousLines", renderLines(entry.getLines()));
        detail.put("newEntryDate", entryDate.toString());
        detail.put("newDescription", asRequest.description());
        detail.put("newLines", renderNewLines(replacementLines));
        auditLog.record("journal-entry.amended", ENTRY_ENTITY, String.valueOf(entryId), detail);

        entry.amendHeader(entryDate, asRequest.description());
        List<JournalLine> replacements = new ArrayList<>();
        for (NewJournalLine line : replacementLines) {
            replacements.add(new JournalLine(line.accountId(), line.side(), line.amount(),
                    line.description(), line.subLedgerRef(), line.vat()));
        }
        entry.replaceLines(replacements);

        return toView(entry, accountsById());
    }

    @Override
    @Transactional
    public JournalEntryView reverse(long entryId, LocalDate reversalDate, String description) {
        Objects.requireNonNull(reversalDate, "reversalDate");

        JournalEntry original = loadWithLines(entryId);
        JournalSource source = original.getSource();

        if (!source.isReversibleThroughTheLedgerAlone()) {
            throw new InvalidJournalEntryException(
                    "Journal entry " + entryId + " came from a " + source + ", which owns state the "
                            + "ledger cannot see" + whatItOwns(source) + ". Reversing the money alone "
                            + "would strand that state, so the reversal has to come from the service "
                            + "that owns it — " + whichServiceReverses(source) + " — which undoes its "
                            + "own state and posts the mirror entry in the same transaction.");
        }
        requireNotAlreadyReversed(entryId);

        String reversalDescription = (description == null || description.isBlank())
                ? "Reversal of entry " + entryId + ": " + original.getDescription()
                : description;

        // Reversing an entry that is itself a reversal is permitted, and needs no special rule: the
        // "reversed at most once" UNIQUE constraint already stops the same entry being reversed twice,
        // and "I reversed the wrong entry" is a real mistake that should be correctable the same way as
        // any other.
        return post(NewJournalEntry.reversalOf(
                entryId, reversalDate, reversalDescription, source, mirrorOf(original)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewJournalLine> mirrorOf(long entryId) {
        return mirrorOf(loadWithLines(entryId));
    }

    private static List<NewJournalLine> mirrorOf(JournalEntry original) {
        return original.getLines().stream()
                .map(line -> new NewJournalLine(
                        line.getAccountId(),
                        line.getSide().opposite(),
                        line.getAmount(),
                        line.getDescription(),
                        line.getSubLedgerRef(),
                        line.getVat()))
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Reading entries
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<JournalEntryView> findEntry(long entryId) {
        return entries.findByIdWithLines(entryId).map(entry -> toView(entry, accountsById()));
    }

    @Override
    @Transactional(readOnly = true)
    public JournalEntryView requireEntry(long entryId) {
        return findEntry(entryId).orElseThrow(() -> new JournalEntryNotFoundException(entryId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntryView> findEntries(Collection<Long> entryIds) {
        Objects.requireNonNull(entryIds, "entryIds");
        if (entryIds.isEmpty()) {
            return List.of();
        }
        return toViews(entries.findAllByIdWithLines(entryIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntryView> entriesBetween(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        return toViews(entries.findBetweenWithLines(from, to));
    }

    /** Batches both extra reads a list of entries needs: the chart, and the reversal links. */
    private List<JournalEntryView> toViews(List<JournalEntry> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, AccountView> accounts = accountsById();
        Map<Long, Long> reversedBy = new LinkedHashMap<>();
        for (Object[] pair : entries.findReversalPairs(
                found.stream().map(JournalEntry::getId).toList())) {
            reversedBy.put((Long) pair[0], (Long) pair[1]);
        }
        return found.stream()
                .map(entry -> toView(entry, accounts, reversedBy.get(entry.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JournalEntrySummaryView> pageOfEntries(
            JournalEntryFilter filter, PageRequest pageRequest) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(pageRequest, "pageRequest");

        // Refused rather than answered with an empty page: "no such account" and "this account has
        // no entries" are different facts, and only one of them is the caller's mistake.
        if (filter.accountId() != null) {
            chartOfAccounts.requireAccount(filter.accountId());
        }

        Page<JournalEntry> page = entries.findAll(
                JournalEntrySpecifications.matching(filter),
                SpringPaging.pageableFor(pageRequest, ENTRY_SORTS, "entryDate"));

        Map<Long, LineSummary> summaries = summarise(page.getContent());
        Map<Long, Long> reversedBy = reversalsOf(page.getContent());

        return SpringPaging.responseFrom(page, entry -> {
            LineSummary summary = summaries.get(entry.getId());
            if (summary == null) {
                // Unreachable: a posted entry always has at least two lines, by the same constraint
                // trigger that makes it balance. Loud rather than a zero total, because a ledger
                // listing showing 0.00 reads as a real figure.
                throw new IllegalStateException(
                        "Journal entry " + entry.getId() + " has no lines. A posted entry always "
                                + "has at least " + NewJournalEntry.MINIMUM_LINES + ".");
            }
            return new JournalEntrySummaryView(
                    entry.getId(),
                    entry.getEntryDate(),
                    entry.getDescription(),
                    entry.getSource(),
                    entry.getReversalOfId(),
                    reversedBy.get(entry.getId()),
                    summary.total(),
                    summary.lineCount());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JournalLineView> pageOfLines(
            long accountId, LocalDate from, LocalDate to, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would "
                            + "look identical to a period with nothing in it.");
        }
        chartOfAccounts.requireAccount(accountId);

        Page<JournalLine> page = lines.findAll(
                JournalEntrySpecifications.linesOfAccount(accountId, from, to),
                SpringPaging.pageableFor(pageRequest, LINE_SORTS, "entry.entryDate"));

        Map<Long, AccountView> accounts = accountsById();
        return SpringPaging.responseFrom(page, line -> toView(line, accounts));
    }

    /**
     * The per-entry totals for one page, in one query.
     *
     * <p>Materialised into plain values here, inside the transaction, rather than returned as rows to
     * be read later — the {@code CLAUDE.md} rule about not handing lazy state across a boundary,
     * applied to a projection.
     */
    private Map<Long, LineSummary> summarise(List<JournalEntry> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        Map<Long, LineSummary> summaries = new LinkedHashMap<>();
        for (Object[] row : entries.summariseLines(page.stream().map(JournalEntry::getId).toList())) {
            summaries.put(
                    (Long) row[0],
                    new LineSummary(
                            new Money(
                                    (BigDecimal) row[1],
                                    Currency.getInstance(((String) row[3]).trim())),
                            ((Number) row[2]).intValue()));
        }
        return summaries;
    }

    private Map<Long, Long> reversalsOf(List<JournalEntry> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> reversedBy = new LinkedHashMap<>();
        for (Object[] pair : entries.findReversalPairs(
                page.stream().map(JournalEntry::getId).toList())) {
            reversedBy.put((Long) pair[0], (Long) pair[1]);
        }
        return reversedBy;
    }

    /** An entry's debits (which are also its credits) and how many lines it has. */
    private record LineSummary(Money total, int lineCount) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalLineView> linesOf(long accountId, LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);
        chartOfAccounts.requireAccount(accountId);
        Map<Long, AccountView> accounts = accountsById();
        return lines.findForAccountBetween(accountId, from, to).stream()
                .map(line -> toView(line, accounts))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalLineView> linesFor(SubLedgerRef subLedgerRef) {
        Objects.requireNonNull(subLedgerRef, "subLedgerRef");
        Map<Long, AccountView> accounts = accountsById();
        return lines.findForSubLedger(subLedgerRef.type(), subLedgerRef.id()).stream()
                .map(line -> toView(line, accounts))
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Balances
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AccountBalance balanceOf(long accountId, LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        AccountView account = chartOfAccounts.requireAccount(accountId);
        return balanceFrom(account, lines.sumBySideForAccountUpTo(accountId, asOf), 0, 1, 2, asOf);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalance balanceOf(AccountSystemKey systemKey, LocalDate asOf) {
        Objects.requireNonNull(systemKey, "systemKey");
        return balanceOf(chartOfAccounts.requireAccount(systemKey).id(), asOf);
    }

    @Override
    @Transactional(readOnly = true)
    public Money subLedgerBalanceOf(SubLedgerRef subLedgerRef, LocalDate asOf) {
        Objects.requireNonNull(subLedgerRef, "subLedgerRef");
        Objects.requireNonNull(asOf, "asOf");

        Money debits = Money.zero(REPORTING_CURRENCY);
        Money credits = Money.zero(REPORTING_CURRENCY);
        for (Object[] row : lines.sumBySideForSubLedgerUpTo(
                subLedgerRef.type(), subLedgerRef.id(), asOf)) {
            Money total = money(row[1], row[2]);
            if (row[0] == BalanceSide.DEBIT) {
                debits = debits.plus(total);
            } else {
                credits = credits.plus(total);
            }
        }
        // Debit-positive, not presented on a normal side: one sub-ledger reference legitimately appears
        // on accounts with opposite normal sides — an asset's cost and its accumulated depreciation both
        // carry the same ASSET reference — so flipping the sign per account first would make their net
        // the sum of the two rather than the carrying value.
        return debits.minus(credits);
    }

    @Override
    @Transactional(readOnly = true)
    public TrialBalance trialBalance(LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");

        // Grouped rows keyed by account, then walked in chart order. Accounts with no activity are
        // omitted: 67 zero rows would bury the ones that matter, and "which accounts have never been
        // posted to" is a different question the chart already answers.
        Map<Long, List<Object[]>> byAccount = new LinkedHashMap<>();
        for (Object[] row : lines.sumBySideForAllAccountsUpTo(asOf)) {
            byAccount.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add(row);
        }

        List<AccountBalance> balances = new ArrayList<>();
        for (AccountView account : chartOfAccounts.allAccounts()) {
            List<Object[]> rows = byAccount.get(account.id());
            if (rows == null) {
                continue;
            }
            balances.add(balanceFrom(account, rows, 1, 2, 3, asOf));
        }
        return new TrialBalance(asOf, REPORTING_CURRENCY, balances);
    }

    // ---------------------------------------------------------------------------------------
    // VAT — Q14
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<VatTotal> vatTotals(LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);

        Map<Long, AccountView> accounts = accountsById();
        // Sorted so the answer is reproducible: direction first, then class id. A report that reorders
        // itself between runs is a report nobody trusts.
        Map<String, Money[]> accumulated = new TreeMap<>();
        Map<String, VatDirection> directions = new LinkedHashMap<>();
        Map<String, Long> classIds = new LinkedHashMap<>();

        for (Object[] row : lines.sumVatBetween(from, to)) {
            AccountView account = accounts.get((Long) row[0]);
            VatDirection direction = account == null
                    ? null
                    : VatDirection.ofAccount(account.systemKey());
            if (direction == null) {
                // Unreachable while the trigger holds: only the two VAT accounts may carry a VAT class.
                // Failing loudly rather than skipping, because a VAT line on some other account means
                // the trigger was dropped, and quietly omitting it would understate the VAT return.
                throw new IllegalStateException(
                        "Journal lines carry a VAT class against account " + row[0] + ", which is "
                                + "neither the OUTPUT_VAT nor the INPUT_VAT account. Only those two "
                                + "may (Q14), and a trigger enforces it — so this means the trigger "
                                + "is missing and the VAT figures cannot be trusted.");
            }
            long vatClassId = (Long) row[1];
            String key = direction.name() + "|" + String.format("%019d", vatClassId);
            directions.put(key, direction);
            classIds.put(key, vatClassId);

            Money base = money(row[3], row[4]);
            Money vat = money(row[3], row[5]);
            Money[] running = accumulated.computeIfAbsent(key,
                    ignored -> new Money[] {Money.zero(base.currency()), Money.zero(vat.currency())});

            // Netted per direction, not per side. Output VAT is normally credited, and a credit note or
            // a reversal debits it; the reportable figure is the net, and which way round that is
            // depends on the account rather than on the line.
            boolean increases = (row[2] == BalanceSide.CREDIT) == (direction == VatDirection.OUTPUT);
            running[0] = increases ? running[0].plus(base) : running[0].minus(base);
            running[1] = increases ? running[1].plus(vat) : running[1].minus(vat);
        }

        List<VatTotal> totals = new ArrayList<>();
        for (Map.Entry<String, Money[]> accumulatedEntry : accumulated.entrySet()) {
            String key = accumulatedEntry.getKey();
            VatClassView vatClass = vatClasses.require(classIds.get(key));
            totals.add(new VatTotal(
                    directions.get(key),
                    vatClass.id(),
                    vatClass.code(),
                    vatClass.ratePercent(),
                    accumulatedEntry.getValue()[0],
                    accumulatedEntry.getValue()[1]));
        }
        return totals;
    }

    // ---------------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------------

    private static void requireOneCurrency(List<NewJournalLine> entryLines) {
        Currency first = entryLines.getFirst().amount().currency();
        for (NewJournalLine line : entryLines) {
            if (!line.amount().currency().equals(first)) {
                throw new InvalidJournalEntryException(
                        "Journal entry mixes " + first.getCurrencyCode() + " and "
                                + line.amount().currency().getCurrencyCode() + " lines. NovoCore "
                                + "does not convert between currencies and will not pick one (ADR "
                                + "0005), so such an entry has no total and cannot be said to "
                                + "balance.");
            }
        }
    }

    private static void requireBalanced(NewJournalEntry request) {
        if (!request.balances()) {
            throw new UnbalancedJournalEntryException(
                    request.totalDebits(), request.totalCredits());
        }
    }

    /**
     * Every rule that relates a line to the account it posts to.
     *
     * <p>Each of these is also a database constraint or trigger. Checking them here first is what turns
     * a driver-level integrity violation naming a trigger into a message naming the account — the same
     * arrangement {@code ChartOfAccountsServiceImpl.createAccount} uses for the control/sub-ledger
     * biconditional.
     *
     * @param isReversal when true, an inactive account is accepted. A posted entry has to stay
     *     correctable after the account it used has been retired, and deactivation is deliberately soft
     *     for exactly this reason — refusing here would make a posted entry permanently uncorrectable by
     *     an ordinary administrative action taken later.
     */
    private void validateLinesAgainstTheChart(List<NewJournalLine> entryLines, boolean isReversal) {
        for (NewJournalLine line : entryLines) {
            AccountView account = chartOfAccounts.requireAccount(line.accountId());

            if (!account.active() && !isReversal) {
                throw new InvalidJournalEntryException(
                        "Account '" + account.name() + "' is inactive, so nothing new may post to it. "
                                + "It was deactivated precisely so that stops happening; a reversal of "
                                + "an entry that already used it is still permitted.");
            }

            if (account.kind() == AccountKind.CONTROL) {
                SubLedgerRef ref = line.subLedgerRef();
                if (ref == null) {
                    throw new InvalidJournalEntryException(
                            "Line on control account '" + account.name() + "' must carry a "
                                    + account.subLedgerType() + " sub-ledger reference (brief §6). A "
                                    + "control account whose lines do not name what they concern "
                                    + "cannot be reconciled against the sub-ledger it exists to "
                                    + "summarise.");
                }
                if (ref.type() != account.subLedgerType()) {
                    throw new InvalidJournalEntryException(
                            "Line on control account '" + account.name() + "' carries a " + ref.type()
                                    + " reference, but that account's sub-ledger is "
                                    + account.subLedgerType() + ". This is what stops a customer "
                                    + "reference landing on an accounts payable line.");
                }
            }

            VatDimension vat = line.vat();
            if (vat == null) {
                continue;
            }
            if (!VatDirection.isVatAccount(account.systemKey())) {
                throw new InvalidJournalEntryException(
                        "Line on '" + account.name() + "' carries a VAT class, but only the Output VAT "
                                + "and Input VAT accounts may (Q14). The taxable base of a revenue or "
                                + "expense line belongs on the invoice line, not on the posting.");
            }
            if (!vat.taxableBase().currency().equals(line.amount().currency())) {
                throw new InvalidJournalEntryException(
                        "VAT line has a taxable base in " + vat.taxableBase().currency()
                                + " and an amount in " + line.amount().currency()
                                + ". A rate applied across two currencies is not a rate (ADR 0005).");
            }
            // Fails loudly on an unknown class rather than storing an id the FK would reject with a
            // constraint name (CLAUDE.md rule 8).
            vatClasses.require(vat.vatClassId());
        }
    }

    private void requireNotAlreadyReversed(long entryId) {
        entries.findByReversalOfId(entryId).ifPresent(existing -> {
            throw new InvalidJournalEntryException(
                    "Journal entry " + entryId + " has already been reversed by entry "
                            + existing.getId() + ". Reversing it twice would double the correction "
                            + "and leave the ledger short by the original amount, with both halves "
                            + "looking individually correct.");
        });
    }

    /**
     * A reversal's lines must be the exact mirror of the original's.
     *
     * <p>This is what makes {@code post} with {@code reversalOfEntryId} set a safe entry point for a
     * service reversing its own document, rather than a second and weaker write path. Without it,
     * "reversal of entry 12" would be an unverified claim, and an entry could be labelled a reversal
     * while posting something else entirely.
     *
     * <p>Compared as a multiset of account, side, amount, sub-ledger reference and VAT dimension.
     * <strong>Line descriptions are excluded</strong> and order is not required to match: a reversal
     * legitimately re-words its lines, and requiring identical prose would refuse a correct reversal for
     * a cosmetic reason.
     */
    private void requireExactMirror(long originalEntryId, List<NewJournalLine> proposed) {
        JournalEntry original = loadWithLines(originalEntryId);
        requireNotAlreadyReversed(originalEntryId);

        List<String> expected = mirrorOf(original).stream()
                .map(JournalServiceImpl::fingerprint).sorted().toList();
        List<String> actual = proposed.stream()
                .map(JournalServiceImpl::fingerprint).sorted().toList();

        if (!expected.equals(actual)) {
            throw new InvalidJournalEntryException(
                    "The proposed reversal of entry " + originalEntryId + " is not its mirror. "
                            + "Expected " + expected + " but got " + actual + ". A reversal has the "
                            + "same accounts, amounts and references with the sides swapped; anything "
                            + "else is a new entry and should be posted as one, so that the ledger "
                            + "does not claim entry " + originalEntryId + " was withdrawn when "
                            + "something different happened.");
        }
    }

    private static String fingerprint(NewJournalLine line) {
        return line.accountId() + "|" + line.side() + "|" + line.amount()
                + "|" + line.subLedgerRef() + "|" + line.vat();
    }

    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with nothing in it.");
        }
    }

    private static String whatItOwns(JournalSource source) {
        return switch (source) {
            case INVENTORY_WRITE_OFF -> " — the lot quantity it reduced";
            case RECEIPT, PAYMENT -> " — the allocations it made against invoices";
            case SALES_INVOICE, CREDIT_NOTE -> " — its open amount, and the serialized units it sold";
            case PURCHASE_INVOICE -> " — its GR/IR clearing position and the lots behind it";
            case GOODS_RECEIPT -> " — the inventory lots it created";
            default -> "";
        };
    }

    private static String whichServiceReverses(JournalSource source) {
        return switch (source) {
            case INVENTORY_WRITE_OFF -> "InventoryService.reverseWriteOff";
            case GOODS_RECEIPT -> "GoodsReceiptService.reverse";
            case PURCHASE_INVOICE -> "PurchaseInvoiceService.reverse";
            default -> "the service that owns that transaction type, which does not exist yet: "
                    + source + " arrives in build step 9";
        };
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private JournalEntry loadWithLines(long entryId) {
        return entries.findByIdWithLines(entryId)
                .orElseThrow(() -> new JournalEntryNotFoundException(entryId));
    }

    private Map<Long, AccountView> accountsById() {
        Map<Long, AccountView> byId = new LinkedHashMap<>();
        for (AccountView account : chartOfAccounts.allAccounts()) {
            byId.put(account.id(), account);
        }
        return byId;
    }

    /**
     * Builds a balance from grouped {@code (side, currency, total)} rows.
     *
     * <p>The index parameters are so that the same assembly serves both the single-account query and the
     * all-accounts one, which carries the account id as its first column. Writing it twice is how the two
     * come to disagree about the sign convention.
     */
    private static AccountBalance balanceFrom(AccountView account, List<Object[]> rows,
            int sideIndex, int currencyIndex, int totalIndex, LocalDate asOf) {
        Money debits = Money.zero(REPORTING_CURRENCY);
        Money credits = Money.zero(REPORTING_CURRENCY);
        for (Object[] row : rows) {
            Money total = money(row[currencyIndex], row[totalIndex]);
            if (row[sideIndex] == BalanceSide.DEBIT) {
                debits = debits.plus(total);
            } else {
                credits = credits.plus(total);
            }
        }
        return new AccountBalance(
                account.id(), account.name(), account.type(), debits, credits, asOf);
    }

    /** A grouped sum and its currency column, as {@code Money}. */
    private static Money money(Object currencyColumn, Object amountColumn) {
        // trim() because a char(3) column comes back space-padded in some drivers, and
        // Currency.getInstance rejects "EUR ".
        return new Money(
                (BigDecimal) amountColumn,
                Currency.getInstance(((String) currencyColumn).trim()));
    }

    private JournalEntryView toView(JournalEntry entry, Map<Long, AccountView> accounts) {
        Long reversedBy = entries.findByReversalOfId(entry.getId())
                .map(JournalEntry::getId)
                .orElse(null);
        return toView(entry, accounts, reversedBy);
    }

    private static JournalEntryView toView(
            JournalEntry entry, Map<Long, AccountView> accounts, Long reversedByEntryId) {
        return new JournalEntryView(
                entry.getId(),
                entry.getEntryDate(),
                entry.getDescription(),
                entry.getSource(),
                entry.getReversalOfId(),
                reversedByEntryId,
                entry.getLines().stream().map(line -> toView(line, accounts)).toList());
    }

    private static JournalLineView toView(JournalLine line, Map<Long, AccountView> accounts) {
        JournalEntry entry = line.getEntry();
        AccountView account = accounts.get(line.getAccountId());
        if (account == null) {
            // Unreachable: journal_line_account_fk guarantees the row exists, and the map is the whole
            // chart. Loud rather than a null name, because a ledger listing with a blank account is the
            // kind of thing that gets read as "no account" instead of "lookup failed".
            throw new IllegalStateException(
                    "Journal line " + line.getId() + " posts to account " + line.getAccountId()
                            + ", which is not in the chart of accounts.");
        }
        return new JournalLineView(
                line.getId(),
                entry.getId(),
                entry.getEntryDate(),
                entry.getSource(),
                entry.getDescription(),
                line.getLineNumber(),
                line.getAccountId(),
                account.name(),
                account.type(),
                line.getSide(),
                line.getAmount(),
                line.getDescription(),
                line.getSubLedgerRef(),
                line.getVat());
    }

    /** A compact, readable rendering of an entry's lines, for the audit log's before/after record. */
    private static String renderLines(List<JournalLine> entryLines) {
        Set<String> rendered = new LinkedHashSet<>();
        for (JournalLine line : entryLines) {
            rendered.add(line.getSide() + " " + line.getAmount() + " account#"
                    + line.getAccountId()
                    + (line.getSubLedgerRef() == null ? "" : " " + line.getSubLedgerRef()));
        }
        return String.join("; ", rendered);
    }

    private static String renderNewLines(List<NewJournalLine> entryLines) {
        Set<String> rendered = new LinkedHashSet<>();
        for (NewJournalLine line : entryLines) {
            rendered.add(line.side() + " " + line.amount() + " account#" + line.accountId()
                    + (line.subLedgerRef() == null ? "" : " " + line.subLedgerRef()));
        }
        return String.join("; ", rendered);
    }
}
