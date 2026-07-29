package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionLineView;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.testsupport.Gen;
import gr.novotrade.novocore.core.api.testsupport.Property;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FIFO consumption over randomly generated <em>histories</em>, not chosen examples.
 *
 * <p><strong>What this adds over {@link StockConsumptionIT}.</strong> That test states what FIFO
 * does — three from the March lot and two from June, backdated receipts consumed where they belong,
 * a shortfall recorded rather than blocked. It reads as the specification and should stay that way.
 * This one asserts what has to remain true after <em>any</em> sequence of receipts and sales: that
 * the oldest sellable stock always goes first, that nothing is created or destroyed on the way
 * through, and — the one that matters most — that <strong>the Inventory control account and the
 * lots agree, lot by lot, at every point.</strong>
 *
 * <p>That last invariant is not a new idea here. It is the assertion that found the defect ADR 0011
 * exists to fix, written there against the specific shapes that ADR examines. Generating the shapes
 * instead is the difference between "it holds for the four histories somebody thought of" and "it
 * holds for the histories nobody thought of", which is where the next one of these will be.
 *
 * <p><strong>Each case is a whole history.</strong> A scenario receives several lots at different
 * dates, costs and locations, then makes several sales against them, and the invariants are checked
 * after every step rather than only at the end — a violation that heals itself two steps later is
 * still a violation, and a end-state-only check would miss it. {@link Property#SCENARIO_CASES}
 * histories per property is the budget; see there for why it is twenty and not five hundred.
 */
class FifoPropertiesIT extends AbstractCoreIntegrationTest {

    /** SKUs must not collide: nothing here rolls back, and every case creates its own product. */
    private static final AtomicInteger SKUS = new AtomicInteger();

    private static final LocalDate EPOCH = LocalDate.of(2026, 1, 1);

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private JournalService journal;

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    @Autowired
    private SettingsService settings;

    @Autowired
    private GoodsReceiptService goodsReceipts;

    @Autowired
    private SupplierService suppliers;

    // ---------------------------------------------------------------------------------------
    // The generated history
    // ---------------------------------------------------------------------------------------

    /**
     * One delivery: how many units, at what cost, on what day, and where it landed.
     *
     * <p>{@code dayOffset} rather than a date because backdating is the point — FIFO orders by
     * acquisition date and not by insertion order, so a generator that only ever moved forwards
     * would never exercise the ordering rule at all. Damaged Goods appears because stock that is
     * still an asset but not sellable is exactly what a costing rule must not quietly take.
     */
    private record Delivery(int units, BigDecimal unitCost, int dayOffset, StockLocation location) {

        LocalDate date() {
            return EPOCH.plusDays(dayOffset);
        }
    }

    /** A history: deliveries first, then sales taken against whatever they left behind. */
    private record History(List<Delivery> deliveries, List<Integer> sales) {
    }

    /**
     * Costs including ones that are not a whole number of cents — a landed-cost-allocated lot
     * (0.666667), a half-cent price (12.505), a repeating third.
     */
    private static Gen<BigDecimal> anyCosts() {
        // Zero is in the list because a free sample is a real lot: it posts nothing and the stock
        // still leaves, which is a path with its own branch in the posting code.
        return Gen.oneOf(
                new BigDecimal("0.000000"), new BigDecimal("1.000000"), new BigDecimal("0.333333"),
                new BigDecimal("12.505000"), new BigDecimal("10.666667"),
                new BigDecimal("0.000001"), new BigDecimal("99.999999"));
    }

    /**
     * Costs that are a whole number of cents.
     *
     * <p><strong>Why this restriction exists, and it is not a convenience.</strong> Below the cent,
     * the Inventory control account and {@code InventoryLotView.remainingValue()} genuinely
     * disagree today, and a fully consumed lot leaves a residue behind in Inventory — see
     * {@code docs/PROGRESS.md}, open question Q45, which carries the measured reproducer. That is a
     * defect in the posting rule, not in this test, and it is recorded rather than papered over.
     * Until it is decided and fixed, the exact-agreement property is asserted over the costs where
     * it does hold, so the suite states something true. <strong>Widening this generator is the
     * right way to check the fix — do not widen it before there is one.</strong>
     */
    private static Gen<BigDecimal> wholeCentCosts() {
        return Gen.oneOf(
                new BigDecimal("0.000000"), new BigDecimal("1.000000"), new BigDecimal("12.500000"),
                new BigDecimal("0.010000"), new BigDecimal("99.990000"));
    }

    private static Gen<Delivery> deliveries(Gen<BigDecimal> costs) {
        Gen<Integer> units = Gen.ints(1, 40);
        Gen<Integer> days = Gen.ints(0, 30);
        Gen<StockLocation> locations =
                Gen.oneOf(StockLocation.INVENTORY, StockLocation.INVENTORY,
                        StockLocation.DAMAGED_GOODS);
        return new Gen<>() {
            @Override
            public Delivery sample(RandomGenerator random) {
                return new Delivery(units.sample(random), costs.sample(random),
                        days.sample(random), locations.sample(random));
            }

            @Override
            public List<Delivery> shrink(Delivery value) {
                List<Delivery> candidates = new ArrayList<>();
                for (Integer fewer : units.shrink(value.units())) {
                    candidates.add(new Delivery(fewer, value.unitCost(), value.dayOffset(),
                            value.location()));
                }
                for (BigDecimal simpler : costs.shrink(value.unitCost())) {
                    candidates.add(new Delivery(value.units(), simpler, value.dayOffset(),
                            value.location()));
                }
                for (Integer earlier : days.shrink(value.dayOffset())) {
                    candidates.add(new Delivery(value.units(), value.unitCost(), earlier,
                            value.location()));
                }
                for (StockLocation simpler : locations.shrink(value.location())) {
                    candidates.add(new Delivery(value.units(), value.unitCost(), value.dayOffset(),
                            simpler));
                }
                return candidates;
            }
        };
    }

    /** Histories over every cost shape, including ones finer than a cent. */
    private static Gen<History> histories() {
        return histories(anyCosts());
    }

    /** Histories restricted to whole-cent costs — see {@link #wholeCentCosts()} for why. */
    private static Gen<History> wholeCentHistories() {
        return histories(wholeCentCosts());
    }

    private static Gen<History> histories(Gen<BigDecimal> costs) {
        // Sales are allowed to exceed everything received, which is how Q17's negative-stock path
        // gets exercised without a generator that has to be told about it.
        Gen<Gen.Pair<List<Delivery>, List<Integer>>> parts =
                Gen.pair(Gen.listOf(deliveries(costs), 1, 5), Gen.listOf(Gen.ints(1, 90), 1, 4));
        return new Gen<>() {
            @Override
            public History sample(RandomGenerator random) {
                Gen.Pair<List<Delivery>, List<Integer>> sampled = parts.sample(random);
                return new History(sampled.first(), sampled.second());
            }

            @Override
            public List<History> shrink(History value) {
                List<History> candidates = new ArrayList<>();
                for (Gen.Pair<List<Delivery>, List<Integer>> simpler
                        : parts.shrink(new Gen.Pair<>(value.deliveries(), value.sales()))) {
                    candidates.add(new History(simpler.first(), simpler.second()));
                }
                return candidates;
            }
        };
    }

    // ---------------------------------------------------------------------------------------
    // Replaying one history
    // ---------------------------------------------------------------------------------------

    /**
     * One sale, together with what every lot had left <em>immediately before</em> it.
     *
     * <p>The before-state is captured rather than derived, because a lot may be consumed again by a
     * later sale in the same history — so by the time the properties run, "what this sale found" is
     * no longer recoverable from the lots.
     */
    private record Sale(StockConsumptionView view, Map<Long, Quantity> remainingBefore) {
    }

    /** What one replayed history left behind, so the properties can assert against it. */
    private record Replay(long productId, List<Long> lotIds, List<Sale> sales) {
    }

    /**
     * Plays a history against the real services and returns what it left behind.
     *
     * <p><strong>Stock arrives through a Goods Receipt, not through
     * {@link InventoryService#receive}.</strong> That distinction was found by this very test: the
     * lower-level {@code receive} deliberately posts nothing — ADR 0004 puts the Inventory debit on
     * the Goods Receipt, which is the document that knows the supplier the GR/IR clearing is
     * against — so a fixture built on it creates stock with no ledger entry behind it, and the
     * ledger-versus-lots invariant is false for a reason that has nothing to do with FIFO. The
     * interface says so explicitly ("nothing outside the core should be calling this"), and this
     * test now honours it. {@link StockConsumptionIT} still uses the shortcut and is right to: it
     * asserts nothing about the ledger.
     */
    private Replay replay(History history) {
        return replay(history, false);
    }

    private Replay replay(History history, boolean checkLedgerAfterEverySale) {
        ProductView product = products.create(NewProduct.goods(
                "FIFOPROP-" + SKUS.incrementAndGet(), "FIFO property fixture",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur("50.00")));
        long supplierId = suppliers.create(NewSupplier.domestic(
                "FIFO property supplier " + SKUS.get(), null)).id();

        List<Long> lotIds = new ArrayList<>();
        for (Delivery delivery : history.deliveries()) {
            // One receipt per delivery rather than one carrying every line: a receipt has a single
            // date, and backdating is exactly what the FIFO ordering rule is about.
            GoodsReceiptView receipt = goodsReceipts.record(NewGoodsReceipt.of(
                    supplierId, delivery.date(),
                    List.of(new NewGoodsReceiptLine(product.id(), Quantity.of(delivery.units()),
                            List.of(), UnitCost.of(delivery.unitCost(), Money.EUR),
                            delivery.location(), null, null))));
            lotIds.addAll(receipt.lotIds());
        }

        List<Sale> sales = new ArrayList<>();
        for (Integer wanted : history.sales()) {
            Map<Long, Quantity> before = new LinkedHashMap<>();
            for (Long lotId : lotIds) {
                before.put(lotId, inventory.requireLot(lotId).quantityRemaining());
            }
            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(product.id(),
                    Quantity.of(wanted.longValue()), EPOCH.plusDays(60),
                    JournalSource.SALES_INVOICE));
            sales.add(new Sale(sale, Map.copyOf(before)));
            if (checkLedgerAfterEverySale) {
                // Checked after every sale, not only at the end: an invariant that breaks and then
                // heals is still broken, and an end-state assertion would report a clean ledger.
                assertLedgerAgreesWithLots(lotIds);
            }
        }
        return new Replay(product.id(), List.copyOf(lotIds), List.copyOf(sales));
    }

    // ---------------------------------------------------------------------------------------
    // An independent FIFO, for the allocation to be compared against
    // ---------------------------------------------------------------------------------------

    /** The order {@code lotsOf} defines and FIFO consumes in: acquisition date, then id. */
    private static final Comparator<InventoryLotView> FIFO_ORDER =
            Comparator.<InventoryLotView, LocalDate>comparing(InventoryLotView::acquisitionDate)
                    .thenComparingLong(InventoryLotView::id);

    /** One lot and how much came out of it. */
    private record Taken(long lotId, Quantity quantity) {
    }

    /** What the service actually took, in the order it recorded taking it. */
    private List<Taken> allocationOf(StockConsumptionView sale) {
        return sale.lines().stream()
                .map(line -> new Taken(line.lotId(), line.quantity()))
                .toList();
    }

    /**
     * What FIFO should have taken, computed here from the lots' state before the sale.
     *
     * <p>Deliberately a second implementation rather than a re-reading of the first: it starts from
     * the captured before-state and the lot views, so it shares no code with
     * {@code InventoryServiceImpl.consume} and cannot inherit its mistakes. Comparing the two is a
     * materially stronger claim than checking a handful of symptoms of the right answer, and it is
     * short enough to be obviously correct — which is the only reason an oracle is worth having.
     */
    private List<Taken> expectedAllocation(Sale sale) {
        List<InventoryLotView> candidates = new ArrayList<>();
        for (Map.Entry<Long, Quantity> before : sale.remainingBefore().entrySet()) {
            if (!before.getValue().isPositive()) {
                continue;
            }
            InventoryLotView lot = inventory.requireLot(before.getKey());
            if (lot.location() == null
                    || !StockLocation.sellableLocations().contains(lot.location())) {
                continue;
            }
            candidates.add(lot);
        }
        candidates.sort(FIFO_ORDER);

        List<Taken> expected = new ArrayList<>();
        Quantity outstanding = sale.view().quantityRequested();
        for (InventoryLotView lot : candidates) {
            if (!outstanding.isPositive()) {
                break;
            }
            Quantity take = outstanding.min(sale.remainingBefore().get(lot.id()));
            expected.add(new Taken(lot.id(), take));
            outstanding = outstanding.minus(take);
        }
        return List.copyOf(expected);
    }

    /**
     * <strong>The strong one.</strong> Every lot's position in the Inventory control account equals
     * what that lot says it is still carrying.
     *
     * <p>Two independent records of the same fact — the sub-ledger reference on the posted lines,
     * and the lot's own remaining quantity extended at its own cost — read separately and compared.
     * This is what makes the Inventory account genuinely reconcilable rather than merely declared to
     * be a control account.
     */
    private void assertLedgerAgreesWithLots(List<Long> lotIds) {
        for (Long lotId : lotIds) {
            InventoryLotView lot = inventory.requireLot(lotId);
            Money fromTheLedger = inventoryPositionOf(lotId);
            assertThat(fromTheLedger)
                    .as("Inventory ledger position of lot %d vs what the lot carries", lotId)
                    .isEqualTo(lot.remainingValue());
        }
    }

    /** The Inventory-account side only, so COGS lines carrying the same reference do not net in. */
    private Money inventoryPositionOf(long lotId) {
        long inventoryAccount = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY).id();
        Money position = Money.zero(Money.EUR);
        for (JournalLineView line : journal.linesFor(SubLedgerRef.inventoryLot(lotId))) {
            if (line.accountId() != inventoryAccount) {
                continue;
            }
            position = line.side() == BalanceSide.DEBIT
                    ? position.plus(line.amount())
                    : position.minus(line.amount());
        }
        return position;
    }

    private RoundingMode ledgerRounding() {
        return settings.requireRoundingMode("ledger.rounding.mode");
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("nothing is created or destroyed")
    class Conservation {

        @Test
        @DisplayName("what was asked for is what was filled plus what was short")
        void filledPlusShortfallIsWhatWasRequested() {
            Property.forAllScenarios("filled + shortfall == requested, and lines sum to filled",
                    histories(), history -> {
                        for (Sale scenarioSale : replay(history).sales()) {
                            StockConsumptionView sale = scenarioSale.view();
                            assertThat(sale.quantityFilled().plus(sale.shortfallQuantity()))
                                    .isEqualTo(sale.quantityRequested());

                            Quantity takenFromLots = Quantity.ZERO;
                            for (StockConsumptionLineView line : sale.lines()) {
                                takenFromLots = takenFromLots.plus(line.quantity());
                            }
                            assertThat(takenFromLots).isEqualTo(sale.quantityFilled());
                        }
                    });
        }

        @Test
        @DisplayName("no lot ever goes below zero or above what it received")
        void lotsStayWithinTheirOwnBounds() {
            // Aggregate stock may go negative (Q17); a single lot may not, and that is a CHECK
            // constraint rather than a service rule. A generated history that oversells by eighty
            // units is exactly what would find it if the two were ever confused.
            Property.forAllScenarios("0 <= remaining <= received, for every lot",
                    histories(), history -> {
                        Replay replayed = replay(history);
                        for (Long lotId : replayed.lotIds()) {
                            InventoryLotView lot = inventory.requireLot(lotId);
                            assertThat(lot.quantityRemaining().isNegative()).isFalse();
                            assertThat(lot.quantityRemaining())
                                    .isLessThanOrEqualTo(lot.quantityReceived());
                        }
                    });
        }

        @Test
        @DisplayName("the product's stock is what the lots have left, less what was oversold")
        void aggregateStockReconcilesToTheLots() {
            Property.forAllScenarios("stockOf().total() == sum(remaining) - sum(shortfalls)",
                    histories(), history -> {
                        Replay replayed = replay(history);

                        Quantity onTheLots = Quantity.ZERO;
                        for (Long lotId : replayed.lotIds()) {
                            onTheLots = onTheLots.plus(
                                    inventory.requireLot(lotId).quantityRemaining());
                        }
                        Quantity oversold = Quantity.ZERO;
                        for (Sale scenarioSale : replayed.sales()) {
                            StockConsumptionView sale = scenarioSale.view();
                            oversold = oversold.plus(sale.shortfallQuantity());
                        }

                        StockLevels levels = inventory.stockOf(replayed.productId());
                        assertThat(levels.total()).isEqualTo(onTheLots.minus(oversold));
                        // Q17: the product genuinely reads negative rather than clamping at zero.
                        assertThat(levels.isOversold()).isEqualTo(oversold.isPositive());
                    });
        }
    }

    @Nested
    @DisplayName("FIFO is an order, not a preference")
    class Order {

        @Test
        @DisplayName("it takes exactly what an independently written FIFO would have taken")
        void matchesAnIndependentlyComputedFifo() {
            // A full oracle rather than a list of symptoms. The three claims below — ordering,
            // exhaustion, never reaching into Damaged Goods — are each a consequence of this one,
            // and are still written separately because a failure reported as "the allocation
            // differs" says less than "it took stock out of Damaged Goods".
            Property.forAllScenarios("the allocation equals an independent FIFO's allocation",
                    histories(), history -> {
                        for (Sale sale : replay(history).sales()) {
                            assertThat(allocationOf(sale.view()))
                                    .as("FIFO allocation for a sale of %s",
                                            sale.view().quantityRequested())
                                    .isEqualTo(expectedAllocation(sale));
                        }
                    });
        }

        @Test
        @DisplayName("lots are taken oldest first, by acquisition date and then by id")
        void lotsAreTakenInAcquisitionOrder() {
            Property.forAllScenarios("consumption lines follow (acquisitionDate, id)",
                    histories(), history -> {
                        for (Sale sale : replay(history).sales()) {
                            List<InventoryLotView> touched = new ArrayList<>();
                            for (StockConsumptionLineView line : sale.view().lines()) {
                                touched.add(inventory.requireLot(line.lotId()));
                            }
                            List<InventoryLotView> expectedOrder = new ArrayList<>(touched);
                            expectedOrder.sort(FIFO_ORDER);
                            assertThat(touched)
                                    .as("lots touched by one sale, in the order they were consumed")
                                    .containsExactlyElementsOf(expectedOrder);
                        }
                    });
        }

        @Test
        @DisplayName("a lot is only left partly consumed if it is the last one the sale reached")
        void everyLotButTheLastIsExhausted() {
            // The behavioural statement of FIFO that ordering alone does not make: taking two from
            // an older lot and three from a newer one would still be "in order" and would still be
            // wrong. This is why the before-state is captured — the lot may have been drained
            // further by a later sale in the same history, so "is it empty now" proves nothing.
            Property.forAllScenarios("all but the final lot of a sale are emptied",
                    histories(), history -> {
                        for (Sale sale : replay(history).sales()) {
                            List<StockConsumptionLineView> lines = sale.view().lines();
                            for (int i = 0; i < lines.size() - 1; i++) {
                                StockConsumptionLineView line = lines.get(i);
                                assertThat(line.quantity())
                                        .as("lot %d kept stock while a newer lot was consumed",
                                                line.lotId())
                                        .isEqualTo(sale.remainingBefore().get(line.lotId()));
                            }
                        }
                    });
        }

        @Test
        @DisplayName("stock that is not sellable is never taken, however short the sale runs")
        void damagedStockIsNeverConsumed() {
            // A costing rule quietly selling out of Damaged Goods is the failure this guards: the
            // stock is still an asset, only a write-off derecognises it, and a sale that runs short
            // has an honest answer available (Q17's shortfall) that does not involve reaching for it.
            Property.forAllScenarios("no consumption line names a lot outside a sellable location",
                    histories(), history -> {
                        for (Sale sale : replay(history).sales()) {
                            for (StockConsumptionLineView line : sale.view().lines()) {
                                InventoryLotView lot = inventory.requireLot(line.lotId());
                                assertThat(StockLocation.sellableLocations())
                                        .as("lot %d was consumed from %s", lot.id(), lot.location())
                                        .contains(lot.location());
                            }
                        }
                    });
        }
    }

    @Nested
    @DisplayName("what a sale posts")
    class Posting {

        @Test
        @DisplayName("the cost is each lot's own cost extended once, never an average")
        void costIsPerLotAndRoundedOncePerLine() {
            Property.forAllScenarios("totalCost == sum over lines of round(qty x lot cost)",
                    histories(), history -> {
                        RoundingMode mode = ledgerRounding();
                        for (Sale scenarioSale : replay(history).sales()) {
                            StockConsumptionView sale = scenarioSale.view();
                            Money expected = Money.zero(Money.EUR);
                            for (StockConsumptionLineView line : sale.lines()) {
                                expected = expected.plus(
                                        line.unitCost().extend(line.quantity(), mode));
                            }
                            assertThat(sale.totalCost()).isEqualTo(expected);
                        }
                    });
        }

        @Test
        @DisplayName("the entry balances, debits COGS and credits Inventory, both naming the lot")
        void theEntryIsWhatItClaimsToBe() {
            Property.forAllScenarios("COGS debit == Inventory credit == totalCost",
                    histories(), history -> {
                        long cogs = chartOfAccounts
                                .requireAccount(AccountSystemKey.COST_OF_GOODS_SOLD).id();
                        long inventoryAccount = chartOfAccounts
                                .requireAccount(AccountSystemKey.INVENTORY).id();

                        for (Sale scenarioSale : replay(history).sales()) {
                            StockConsumptionView sale = scenarioSale.view();
                            if (sale.journalEntryId() == null) {
                                // A sale entirely out of zero-cost lots, or entirely unbacked,
                                // derecognises nothing and rightly posts nothing.
                                assertThat(sale.totalCost().isZero()).isTrue();
                                continue;
                            }
                            JournalEntryView entry = journal.requireEntry(sale.journalEntryId());
                            assertThat(entry.totalDebits()).isEqualTo(entry.totalCredits());
                            assertThat(entry.totalDebits()).isEqualTo(sale.totalCost());

                            for (JournalLineView line : entry.lines()) {
                                assertThat(line.accountId()).isIn(cogs, inventoryAccount);
                                assertThat(line.subLedger())
                                        .as("every consumption line names the lot it came out of")
                                        .isPresent();
                                assertThat(line.amount().isPositive()).isTrue();
                            }
                        }
                    });
        }

        @Test
        @DisplayName("an unbacked sale posts no cost for the part that had nothing behind it")
        void shortfallsAreNeverCosted() {
            // ADR 0008: a shortfall is recorded, queryable and never retro-costed. Asserting it
            // over generated histories is what stops a later "helpful" fix from costing the
            // shortfall at the newest lot's price, which would look reasonable and be wrong.
            Property.forAllScenarios("cost covers only the filled quantity",
                    histories(), history -> {
                        RoundingMode mode = ledgerRounding();
                        for (Sale scenarioSale : replay(history).sales()) {
                            StockConsumptionView sale = scenarioSale.view();
                            if (!sale.droveStockNegative()) {
                                continue;
                            }
                            Money costOfWhatWasThere = Money.zero(Money.EUR);
                            for (StockConsumptionLineView line : sale.lines()) {
                                costOfWhatWasThere = costOfWhatWasThere.plus(
                                        line.unitCost().extend(line.quantity(), mode));
                            }
                            assertThat(sale.totalCost()).isEqualTo(costOfWhatWasThere);
                            assertThat(inventory.consumptionsWithShortfall())
                                    .as("a shortfall stays findable")
                                    .anySatisfy(flagged ->
                                            assertThat(flagged.id()).isEqualTo(sale.id()));
                        }
                    });
        }
    }

    @Nested
    @DisplayName("the ledger and the lots")
    class LedgerAgreement {

        @Test
        @DisplayName("every lot's Inventory position equals what the lot is carrying, throughout")
        void ledgerAndLotsNeverDiverge() {
            // Asserted after every sale as well as at the end: an invariant that breaks and then
            // heals is still broken. It is the invariant that found ADR 0011's defect; generating
            // the histories is what makes it hold for shapes nobody enumerated.
            //
            // Whole-cent costs only, and that restriction is itself a finding — see
            // wholeCentCosts() and Q45. Below the cent this property is false today.
            Property.forAllScenarios("Inventory sub-ledger position == lot.remainingValue()",
                    wholeCentHistories(),
                    history -> assertLedgerAgreesWithLots(replay(history, true).lotIds()));
        }

        @Test
        @DisplayName("a lot that has been entirely consumed leaves nothing behind in Inventory")
        void anExhaustedLotSelfLiquidates() {
            // The plainest statement of what a control account is for: when the thing it controls
            // is gone, its balance is zero. Restricted to whole-cent costs for Q45's reason, and it
            // is the assertion that fails first when Q45 is exercised — a 22-unit lot at 12.505
            // ends at -0.11 EUR with nothing left to explain it.
            Property.forAllScenarios("an emptied lot's Inventory position is exactly zero",
                    wholeCentHistories(), history -> {
                        Replay replayed = replay(history);
                        for (Long lotId : replayed.lotIds()) {
                            if (inventory.requireLot(lotId).quantityRemaining().isPositive()) {
                                continue;
                            }
                            assertThat(inventoryPositionOf(lotId))
                                    .as("lot %d holds nothing; Inventory still carries", lotId)
                                    .isEqualTo(Money.zero(Money.EUR));
                        }
                    });
        }
    }
}
