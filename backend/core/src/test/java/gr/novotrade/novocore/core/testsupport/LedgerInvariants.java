package gr.novotrade.novocore.core.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.TrialBalance;
import gr.novotrade.novocore.core.api.ledger.VatTotal;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.settlement.PartyType;
import gr.novotrade.novocore.core.api.settlement.SettlementService;
import gr.novotrade.novocore.core.api.settlement.SettlementView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <strong>Every invariant this system has, over whatever is in the database — defined once.</strong>
 *
 * <p>Extracted in step 15a from {@code WholeScenarioIT}, which had built the whole set and welded it
 * to a single test class. Step 15 needs exactly the same questions asked of a database that was
 * built <em>only through HTTP</em>, and the one thing that must not happen is a second copy: two
 * statements of "the Inventory account equals what the lots carry" are two statements that can
 * disagree, and the one that stops being updated is always the copy.
 *
 * <p><strong>Why this is a list of invariants and not one {@code sweepAll()} method.</strong> A
 * single method asserting twelve things reports the first failure and hides the other eleven, and
 * {@code WholeScenarioIT}'s own header says why that is unacceptable here: a break in VAT has to
 * tell you it is VAT. So each invariant is its own object with its own name, and {@link
 * #all(LocalDate, LocalDate)} hands the whole set to a JUnit {@code @TestFactory}, which reports one
 * result per invariant. Adding an invariant here covers every caller the same day, with no caller
 * edited.
 *
 * <p><strong>Scope: only what holds over <em>any</em> database.</strong> Everything here is true of
 * an empty ledger, of one document, and of a trading year. Claims that depend on a particular
 * scenario — "the variance is positive", "this bundle line has three components", "the freight
 * invoice is fully spent" — deliberately stayed behind in the test that builds that scenario, where
 * they can name the document they disagree with. {@link #theLedgerIsNotTrivial} is the one
 * parameterised exception, and it exists precisely because everything else here passes perfectly on
 * an empty database.
 *
 * <p>Several of these go <strong>straight to the tables in SQL</strong>, bypassing every service,
 * view and Java-side check. That is deliberate and it is what makes them statements about the data
 * rather than about the code that produced it — the same reason {@code JournalIT}'s probes are
 * written in raw SQL.
 */
public final class LedgerInvariants {

    /**
     * One named check. The name is what a caller reports, so it reads as a claim about the system
     * rather than as a method name.
     */
    public record Invariant(String name, Runnable check) {

        public void run() {
            check.run();
        }
    }

    private final JdbcTemplate jdbc;
    private final ChartOfAccountsService chart;
    private final JournalService journal;
    private final InventoryService inventory;
    private final PurchaseInvoiceService purchaseInvoices;
    private final GoodsReceiptService goodsReceipts;
    private final FreightAllocationService freight;
    private final SettlementService settlements;

    /**
     * The dependencies are listed explicitly rather than resolved from a context, so this
     * constructor is the documentation of exactly what the sweep reads.
     */
    public LedgerInvariants(
            JdbcTemplate jdbc,
            ChartOfAccountsService chart,
            JournalService journal,
            InventoryService inventory,
            PurchaseInvoiceService purchaseInvoices,
            GoodsReceiptService goodsReceipts,
            FreightAllocationService freight,
            SettlementService settlements) {
        this.jdbc = jdbc;
        this.chart = chart;
        this.journal = journal;
        this.inventory = inventory;
        this.purchaseInvoices = purchaseInvoices;
        this.goodsReceipts = goodsReceipts;
        this.freight = freight;
        this.settlements = settlements;
    }

    /** What both callers actually use; the explicit constructor stays as the statement of intent. */
    public static LedgerInvariants from(ApplicationContext context) {
        return new LedgerInvariants(
                context.getBean(JdbcTemplate.class),
                context.getBean(ChartOfAccountsService.class),
                context.getBean(JournalService.class),
                context.getBean(InventoryService.class),
                context.getBean(PurchaseInvoiceService.class),
                context.getBean(GoodsReceiptService.class),
                context.getBean(FreightAllocationService.class),
                context.getBean(SettlementService.class));
    }

    /**
     * The whole universal set, in the order a reader would want to see them fail: the crude total
     * statements about the data first, then the reconciliations, then the derived figures.
     *
     * @param from the start of the period the period-scoped invariants read
     * @param to   the date balances are taken as of; must be on or after every document's date
     */
    public List<Invariant> all(LocalDate from, LocalDate to) {
        return List.of(
                new Invariant("no entry anywhere is unbalanced, empty or one-sided",
                        this::noEntryIsUnbalanced),
                new Invariant("no journal line states nothing: every amount is strictly positive",
                        this::noLineIsZeroOrNegative),
                new Invariant("the trial balance balances",
                        () -> theTrialBalanceBalances(to)),
                new Invariant("every control account equals the sum of its own sub-ledger",
                        () -> controlAccountsReconcileToTheirSubLedgers(to)),
                new Invariant("every Control-account line carries a sub-ledger reference",
                        this::everyControlLineCarriesAReference),
                new Invariant("every sub-ledger reference names a row that exists",
                        this::everySubLedgerReferenceIsLive),
                new Invariant("the Inventory account equals what every lot says it is carrying",
                        () -> inventoryAgreesWithTheLots(to)),
                new Invariant("open items equal the receivable and payable control accounts",
                        () -> openItemsEqualTheControlAccounts(to)),
                new Invariant("no settlement allocates more than it received",
                        () -> noSettlementOverAllocates(from, to)),
                new Invariant("output and input VAT are separate, and each equals its own lines",
                        () -> vatTotalsAgreeWithTheVatAccounts(from, to)),
                new Invariant("purchase price variance equals what the invoices recorded",
                        () -> purchaseVarianceAgreesWithTheInvoices(from, to)),
                new Invariant("landed cost variance is exactly its allocations and its catch-ups",
                        () -> landedCostVarianceDecomposes(from, to)));
    }

    // ===================================================================================
    // Total statements about the data, asked in SQL
    // ===================================================================================

    /**
     * {@code CLAUDE.md} rule 6, asserted against the data rather than against the code. The deferred
     * constraint trigger should make every one of these impossible; this asks whether it did, over
     * every entry that exists.
     */
    public void noEntryIsUnbalanced() {
        List<Map<String, Object>> broken = jdbc.queryForList("""
                SELECT e.id,
                       count(l.id)                                                    AS line_count,
                       coalesce(sum(l.amount) FILTER (WHERE l.side = 'DEBIT'),  0)     AS debits,
                       coalesce(sum(l.amount) FILTER (WHERE l.side = 'CREDIT'), 0)     AS credits,
                       count(DISTINCT l.amount_currency)                              AS currencies
                FROM   journal_entry e
                LEFT   JOIN journal_line l ON l.entry_id = e.id
                GROUP  BY e.id
                HAVING coalesce(sum(l.amount) FILTER (WHERE l.side = 'DEBIT'),  0)
                    <> coalesce(sum(l.amount) FILTER (WHERE l.side = 'CREDIT'), 0)
                    OR count(l.id) < 2
                    OR count(DISTINCT l.amount_currency) <> 1
                ORDER  BY e.id
                """);

        assertThat(broken)
                .as("journal entries that do not balance, have fewer than two lines, or span "
                        + "currencies — CLAUDE.md rule 6, asserted against the data")
                .isEmpty();
    }

    /**
     * A named side plus a strictly positive amount, never signed. A zero line balances while saying
     * nothing, which is why it is refused as firmly as a negative one.
     */
    public void noLineIsZeroOrNegative() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM journal_line WHERE amount <= 0", Long.class))
                .as("journal lines with a zero or negative amount")
                .isZero();
    }

    /**
     * The trigger enforces this on write. Asserted again over the finished ledger, because a line on
     * a control account with no reference is a balance that can never be explained, and {@link
     * #controlAccountsReconcileToTheirSubLedgers} would silently under-count rather than fail.
     */
    public void everyControlLineCarriesAReference() {
        assertThat(jdbc.queryForList("""
                SELECT l.id, a.name, l.sub_ledger_type, l.sub_ledger_id
                FROM   journal_line l
                JOIN   account a ON a.id = l.account_id
                WHERE  a.account_kind = 'CONTROL'
                AND   (l.sub_ledger_type IS NULL OR l.sub_ledger_id IS NULL)
                """))
                .as("Control-account lines with no sub-ledger reference")
                .isEmpty();
    }

    /**
     * <strong>New in step 15a, and the strengthening the previous check stopped short of.</strong>
     * {@code everyControlLineCarriesAReference} asks whether a reference is present; this asks
     * whether it points at anything. The reference is polymorphic and therefore cannot be a foreign
     * key, so nothing but a trigger stands behind it — and a trigger fires on write, which says
     * nothing about a row deleted afterwards.
     *
     * <p>The type-to-table mapping is the same one V15's trigger uses. Asserted by joining rather
     * than by dynamic SQL, so an unmapped {@link SubLedgerType} shows up as an unchecked type in the
     * message instead of as a silent pass.
     */
    public void everySubLedgerReferenceIsLive() {
        Map<SubLedgerType, String> tables = Map.of(
                SubLedgerType.CUSTOMER, "customer",
                SubLedgerType.SUPPLIER, "supplier",
                SubLedgerType.INVENTORY_LOT, "inventory_lot",
                SubLedgerType.ASSET, "asset");

        assertThat(tables.keySet())
                .as("every SubLedgerType must be mapped to a table here, or its references go "
                        + "unchecked while this test still passes")
                .containsExactlyInAnyOrder(SubLedgerType.values());

        List<String> dangling = new ArrayList<>();
        tables.forEach((type, table) -> {
            List<Map<String, Object>> orphans = jdbc.queryForList("""
                    SELECT l.id AS line_id, l.sub_ledger_id
                    FROM   journal_line l
                    LEFT   JOIN %s t ON t.id = l.sub_ledger_id
                    WHERE  l.sub_ledger_type = ?
                    AND    l.sub_ledger_id IS NOT NULL
                    AND    t.id IS NULL
                    """.formatted(table), type.name());
            orphans.forEach(row -> dangling.add(
                    "journal_line " + row.get("line_id") + " → " + type + "#"
                            + row.get("sub_ledger_id") + " (absent from " + table + ")"));
        });

        assertThat(dangling)
                .as("journal lines whose sub-ledger reference points at a row that is not there")
                .isEmpty();
    }

    // ===================================================================================
    // Reconciliations
    // ===================================================================================

    /** Rule 6 restated for the whole ledger. */
    public void theTrialBalanceBalances(LocalDate asOf) {
        TrialBalance trialBalance = journal.trialBalance(asOf);
        assertThat(trialBalance.isBalanced()).isTrue();
        assertThat(trialBalance.difference().isZero()).isTrue();
        assertThat(trialBalance.totalDebits()).isEqualTo(trialBalance.totalCredits());
    }

    /**
     * What makes a Control account reconcilable rather than merely declared to be one: the account's
     * balance and the sum of its per-entity positions are the same rows read two ways, so they
     * cannot disagree unless a line carries a reference it should not.
     */
    public void controlAccountsReconcileToTheirSubLedgers(LocalDate asOf) {
        for (AccountView account : chart.allAccounts()) {
            if (account.subLedgerType() == null) {
                continue;
            }
            Money fromTheAccount = journal.balanceOf(account.id(), asOf).net();
            Money fromTheSubLedger = Money.zero(Money.EUR);
            for (Long entityId : subLedgerEntityIdsOn(account.id())) {
                fromTheSubLedger = fromTheSubLedger.plus(
                        positionOnAccount(account.id(), account.subLedgerType(), entityId));
            }
            assertThat(fromTheSubLedger)
                    .as("control account '%s' vs the sum of its %s sub-ledger",
                            account.name(), account.subLedgerType())
                    .isEqualTo(fromTheAccount);
        }
    }

    /** The invariant that found ADR 0011's defect, and that ADR 0015 restored by construction. */
    public void inventoryAgreesWithTheLots(LocalDate asOf) {
        Money fromTheLots = Money.zero(Money.EUR);
        for (InventoryLotView lot : allLots()) {
            fromTheLots = fromTheLots.plus(lot.remainingValue());
        }
        assertThat(journal.balanceOf(AccountSystemKey.INVENTORY, asOf).net())
                .as("Inventory control account vs the sum of every lot's remaining value")
                .isEqualTo(fromTheLots);
    }

    /**
     * ADR 0009: open item matching is a layer over AR/AP and posts nothing, so the two must agree by
     * construction. If they ever diverge, an allocation has posted something.
     */
    public void openItemsEqualTheControlAccounts(LocalDate asOf) {
        Money openReceivables = Money.zero(Money.EUR);
        for (var item : settlements.allOpenItems(PartyType.CUSTOMER)) {
            openReceivables = openReceivables.plus(item.openAmount());
        }
        Money openPayables = Money.zero(Money.EUR);
        for (var item : settlements.allOpenItems(PartyType.SUPPLIER)) {
            openPayables = openPayables.plus(item.openAmount());
        }

        assertThat(journal.balanceOf(AccountSystemKey.ACCOUNTS_RECEIVABLE, asOf).net())
                .as("Accounts Receivable vs the open sales invoices and credit notes")
                .isEqualTo(openReceivables);
        assertThat(journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, asOf).net().negated())
                .as("Accounts Payable vs the open purchase invoices")
                .isEqualTo(openPayables);
    }

    public void noSettlementOverAllocates(LocalDate from, LocalDate to) {
        for (SettlementView settlement : settlements.between(from, to)) {
            assertThat(settlement.allocatedAmount())
                    .as("settlement %d allocated more than it was", settlement.id())
                    .isLessThanOrEqualTo(settlement.amount());
        }
    }

    // ===================================================================================
    // Derived figures — each read two independent ways
    // ===================================================================================

    /**
     * Q14: never netted. The two figures a VAT return is made of are read off the dimension every
     * VAT line carries, and compared against the accounts those lines posted to — two readings of
     * the same rows, which is the only kind of check worth having here.
     *
     * <p>Deliberately asserts the equality only. That the year charged any output VAT at all is a
     * claim about a scenario, and it belongs with {@link #theLedgerIsNotTrivial}.
     */
    public void vatTotalsAgreeWithTheVatAccounts(LocalDate from, LocalDate to) {
        Money output = Money.zero(Money.EUR);
        Money input = Money.zero(Money.EUR);
        for (VatTotal total : journal.vatTotals(from, to)) {
            switch (total.direction()) {
                case OUTPUT -> output = output.plus(total.vatAmount());
                case INPUT -> input = input.plus(total.vatAmount());
            }
        }

        assertThat(journal.balanceOf(AccountSystemKey.OUTPUT_VAT, to).net().negated())
                .as("Output VAT account vs the sum of the output side")
                .isEqualTo(output);
        assertThat(journal.balanceOf(AccountSystemKey.INPUT_VAT, to).net())
                .as("Input VAT account vs the sum of the input side")
                .isEqualTo(input);
    }

    /**
     * ADR 0008's variance is a residual — the amount that could not go on a lot. Each invoice stores
     * its own figure, so the account and the documents are two independent records of one thing.
     */
    public void purchaseVarianceAgreesWithTheInvoices(LocalDate from, LocalDate to) {
        assertThat(journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, to).net())
                .as("purchase price variance vs what the invoices recorded")
                .isEqualTo(purchaseInvoices.totalVarianceBetween(from, to));
    }

    /**
     * Landed cost variance has two contributors and only a whole-scenario view shows both: an
     * allocation puts the share belonging to stock already gone into it (ADR 0010), and ADR 0011's
     * catch-up takes some back out when returned stock re-enters a re-costed lot.
     *
     * <p>Comparing the account against the allocations alone is wrong, and {@code WholeScenarioIT}
     * found that the hard way — 18.38 against 22.98 allocated, the 4.60 being a credit note's four
     * returned units catching their freight up. So the invariant is a decomposition: the allocation
     * side equals what the allocations recorded, and the account equals both sides together.
     */
    public void landedCostVarianceDecomposes(LocalDate from, LocalDate to) {
        long account = chart.requireAccount(AccountSystemKey.LANDED_COST_VARIANCE).id();
        Money allocated = Money.zero(Money.EUR);
        Money caughtUpByReturns = Money.zero(Money.EUR);
        for (var line : journal.linesOf(account, from, to)) {
            if (line.source() == JournalSource.FREIGHT_ALLOCATION) {
                allocated = allocated.plus(line.debitPositiveEffect());
            } else {
                caughtUpByReturns = caughtUpByReturns.plus(line.debitPositiveEffect());
            }
        }

        Money fromAllocations = Money.zero(Money.EUR);
        for (FreightAllocationView allocation : freight.between(from, to)) {
            fromAllocations = fromAllocations.plus(allocation.variance());
        }

        assertThat(allocated)
                .as("what the allocations put into landed cost variance vs what they recorded")
                .isEqualTo(fromAllocations);
        assertThat(journal.balanceOf(AccountSystemKey.LANDED_COST_VARIANCE, to).net())
                .as("the account is exactly the two contributions together")
                .isEqualTo(allocated.plus(caughtUpByReturns));
    }

    // ===================================================================================
    // Conditional and parameterised — not in all(), because each needs the caller to supply
    // what its own scenario claims
    // ===================================================================================

    /**
     * ADR 0004's clearing account earning its name: everything matched has netted out, and what is
     * left is the timing difference.
     *
     * <p><strong>Not in {@link #all}, because it needs a tolerance the caller has to state.</strong>
     * Step 8 accepted a residual — one receipt line matched by two invoices can leave a cent in
     * GR/IR, and the account is {@code expected_to_clear} precisely so that such a residual is
     * visible rather than impossible. A scenario that avoids the partial-match case passes
     * {@link Money#zero}; one that does not has to say what it accepts, out loud, at the call site.
     *
     * @param tolerance the largest residual this scenario is willing to call noise
     */
    public void grIrHoldsOnlyTheTimingGap(LocalDate asOf, Money tolerance) {
        assertThat(purchaseInvoices.linesAwaitingDelivery())
                .as("invoice lines with no delivery behind them")
                .isEmpty();
        assertThat(goodsReceipts.linesAwaitingInvoice())
                .as("deliveries with no invoice against them")
                .isEmpty();

        Money clearing = journal
                .balanceOf(AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, asOf).net();
        Money magnitude = clearing.isNegative() ? clearing.negated() : clearing;
        assertThat(magnitude)
                .as("GR/IR is %s while nothing on either side is outstanding", clearing)
                .isLessThanOrEqualTo(tolerance);
    }

    /**
     * Guards the failure mode every whole-system test has: <strong>passing because it did
     * nothing.</strong> An empty database satisfies every other invariant here perfectly.
     *
     * <p>Parameterised rather than fixed, because "substantial" means something different to a
     * service-level trading year and to an HTTP-driven quarter, and a threshold inherited from
     * another scenario is one nobody chose.
     */
    public void theLedgerIsNotTrivial(
            LocalDate asOf, long minimumEntries, long minimumLines, int minimumAccountsWithActivity) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_entry", Long.class))
                .as("journal entries produced by the scenario")
                .isGreaterThan(minimumEntries);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_line", Long.class))
                .as("journal lines produced by the scenario")
                .isGreaterThan(minimumLines);
        assertThat(journal.trialBalance(asOf).balances())
                .as("accounts with activity")
                .hasSizeGreaterThan(minimumAccountsWithActivity);
    }

    /**
     * The distinct {@link JournalSource} values the scenario actually posted through. A caller
     * asserts against this to prove its narrative exercised the breadth it claims, rather than
     * producing a hundred entries through one code path.
     */
    public List<String> sourcesPosted() {
        return jdbc.queryForList(
                "SELECT DISTINCT source FROM journal_entry ORDER BY source", String.class);
    }

    // ===================================================================================
    // Readers
    // ===================================================================================

    private List<InventoryLotView> allLots() {
        List<InventoryLotView> lots = new ArrayList<>();
        for (Long productId : jdbc.queryForList(
                "SELECT DISTINCT product_id FROM inventory_lot", Long.class)) {
            lots.addAll(inventory.lotsOf(productId));
        }
        return lots;
    }

    private List<Long> subLedgerEntityIdsOn(long accountId) {
        return jdbc.queryForList(
                "SELECT DISTINCT sub_ledger_id FROM journal_line "
                        + "WHERE account_id = ? AND sub_ledger_id IS NOT NULL",
                Long.class, accountId);
    }

    /** One entity's position on one account, debit-positive — the account's own side only. */
    private Money positionOnAccount(long accountId, SubLedgerType type, long entityId) {
        Money position = Money.zero(Money.EUR);
        for (var line : journal.linesFor(SubLedgerRef.of(type, entityId))) {
            if (line.accountId() != accountId) {
                continue;
            }
            position = line.isDebit()
                    ? position.plus(line.amount())
                    : position.minus(line.amount());
        }
        return position;
    }
}
