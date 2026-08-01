package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Step 14b's purchasing and inventory surface, over real HTTP.
 *
 * <p>The centrepiece is a whole two-document flow driven entirely through the API — record an
 * invoice, receive against it, then read the GR/IR position from both sides. That is the first time
 * anything other than a test harness has driven the purchasing services, and it is what the step is
 * for.
 *
 * <p>Alongside it, three claims that would each fail silently if wrong: that the lower inventory
 * layer has <strong>no route at all</strong>, that PURCHASING and INVENTORY are genuinely separate
 * grants, and that a nested document round-trips its quantities and costs exactly.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + PurchasingEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + PurchasingEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class PurchasingEndpointIT {

    static final String OWNER_USERNAME = "purch.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String BUYER_USERNAME = "purch.buyer";
    private static final String BUYER_PASSWORD = "buyer-password-long-enough";

    /** Unique per call, so repeated runs against one container cannot collide. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserService users;

    @Autowired
    private RoleService roles;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private ProductService products;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    private ApiClient api;
    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    // -------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the two-document flow, driven entirely through the API")
    class TheTwoDocumentFlow {

        @Test
        @DisplayName("invoice first: the receipt matches it and GR/IR clears exactly")
        void invoiceFirstClearsExactly() {
            long supplierId = supplier();
            TestProduct product = product();

            long invoiceId = idOf(created(owner.post("/api/purchase-invoices", """
                    {"supplierId": %d,
                     "supplierInvoiceNumber": "INV-%d",
                     "invoiceDate": "2026-03-01",
                     "description": "Coffee beans",
                     "lines": [{
                       "type": "INVENTORY",
                       "productId": %d,
                       "quantity": "10.000000",
                       "unitPrice": {"amount": "12.505000", "currency": "EUR"},
                       "vatClassId": %d,
                       "reverseCharge": false
                     }]}
                    """.formatted(supplierId, next(), product.id(), standardVatClassId()))));

            // Keyed off the SKU, which is unique per test — a line view carries no parent id, and
            // matching on a bare "id":N would collide with every other line in the list.
            assertThat(owner.get("/api/purchase-invoice-lines/awaiting-delivery").getBody())
                    .contains(product.sku());

            long lineId = firstLineIdOf(owner.get("/api/purchase-invoices/" + invoiceId).getBody());

            ResponseEntity<String> receipt = owner.post("/api/goods-receipts", """
                    {"supplierId": %d,
                     "deliveryNoteNumber": "DN-%d",
                     "receiptDate": "2026-03-05",
                     "lines": [{
                       "productId": %d,
                       "quantity": "10.000000",
                       "location": "INVENTORY",
                       "purchaseInvoiceLineId": %d
                     }]}
                    """.formatted(supplierId, next(), product.id(), lineId));

            assertThat(receipt.getStatusCode())
                    .as("receipt body: %s", receipt.getBody())
                    .isEqualTo(HttpStatus.CREATED);
            // Matched on creation of the second document. There is deliberately no later matching
            // operation — it would need a journal entry belonging to no document (Q41).
            assertThat(owner.get("/api/purchase-invoices/" + invoiceId + "/gr-ir-matches").getBody())
                    .contains("\"items\":[{");
            // Cleared exactly: the line is off the awaiting-delivery list entirely.
            assertThat(owner.get("/api/purchase-invoice-lines/awaiting-delivery").getBody())
                    .doesNotContain(product.sku());
        }

        @Test
        @DisplayName("goods first: the delivery waits for an invoice, and is visible while it does")
        void goodsFirstIsVisibleWhileWaiting() {
            long supplierId = supplier();
            TestProduct product = product();

            ResponseEntity<String> receipt = owner.post("/api/goods-receipts", """
                    {"supplierId": %d,
                     "deliveryNoteNumber": "DN-%d",
                     "receiptDate": "2026-03-05",
                     "lines": [{
                       "productId": %d,
                       "quantity": "4.000000",
                       "unitCost": {"amount": "9.500000", "currency": "EUR"},
                       "location": "INVENTORY"
                     }]}
                    """.formatted(supplierId, next(), product.id()));

            assertThat(receipt.getStatusCode())
                    .as("receipt body: %s", receipt.getBody())
                    .isEqualTo(HttpStatus.CREATED);

            // An unmatched GR/IR balance is not an error — the account is expected_to_clear
            // precisely because a timing gap in either direction is normal. It has to be visible.
            assertThat(owner.get("/api/goods-receipt-lines/awaiting-invoice").getBody())
                    .contains(product.sku());
        }

        @Test
        @DisplayName("a six-decimal unit cost survives the round trip exactly")
        void aSubCentCostSurvives() {
            long supplierId = supplier();
            TestProduct product = product();

            String body = created(owner.post("/api/purchase-invoices", """
                    {"supplierId": %d,
                     "supplierInvoiceNumber": "INV-%d",
                     "invoiceDate": "2026-03-01",
                     "lines": [{
                       "type": "INVENTORY",
                       "productId": %d,
                       "quantity": "22.000000",
                       "unitPrice": {"amount": "12.505000", "currency": "EUR"},
                       "vatClassId": %d,
                       "reverseCharge": false
                     }]}
                    """.formatted(supplierId, next(), product.id(), standardVatClassId())));

            // 12.505 on a 22-unit lot is the exact shape that produced Q45. The wire format has to
            // carry it without touching it.
            assertThat(body).contains("\"amount\":\"12.505000\"");
            assertThat(body).doesNotContain(":12.505");
            assertThat(body).contains("\"22.000000\"");
        }
    }

    @Nested
    @DisplayName("what has deliberately no route")
    class NoRoute {

        @Test
        @DisplayName("there is no endpoint that receives stock without a document")
        void thereIsNoDirectReceiveEndpoint() {
            // Not a route that refuses — a route that does not exist. InventoryService.receive
            // creates a lot and posts nothing; the Goods Receipt calls it and posts the entry in
            // the same transaction. An architecture rule stops a controller reaching it, and this
            // is the observable consequence.
            assertThat(owner.post("/api/inventory/lots", "{}").getStatusCode())
                    .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(owner.post("/api/inventory/consumptions", "{}").getStatusCode())
                    .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("a purchase invoice cannot be edited or deleted — it is somebody else's document")
        void aPurchaseInvoiceIsImmutable() {
            long invoiceId = anInvoice();

            assertThat(owner.patch("/api/purchase-invoices/" + invoiceId, "{}").getStatusCode())
                    .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(owner.delete("/api/purchase-invoices/" + invoiceId).getStatusCode())
                    .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("reversing leaves both documents standing")
        void reversalIsNotDeletion() {
            long invoiceId = anInvoice();

            ResponseEntity<String> reversal = owner.post(
                    "/api/purchase-invoices/" + invoiceId + "/reversal",
                    "{\"reversalDate\": \"2026-03-10\", \"reason\": \"Wrong supplier\"}");

            assertThat(reversal.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            // The original is still readable. A DELETE-shaped API would have implied otherwise.
            assertThat(owner.get("/api/purchase-invoices/" + invoiceId).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("PURCHASING and INVENTORY are different grants")
    class SectionSeparation {

        @Test
        @DisplayName("a buyer who may read invoices may not read lots — a lot carries its cost")
        void purchasingDoesNotGrantInventory() {
            ApiClient.Session buyer = buyerSession();

            assertThat(buyer.get("/api/purchase-invoices?from=2026-01-01&to=2026-12-31")
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(buyer.get("/api/inventory/lots?productId=1").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("a buyer with VIEW may not record an invoice")
        void viewDoesNotGrantRecording() {
            ApiClient.Session buyer = buyerSession();

            assertThat(buyer.post("/api/purchase-invoices", "{}").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("inventory reads and the write-off")
    class InventoryReads {

        @Test
        @DisplayName("a received lot is readable, and a write-off reduces it and posts")
        void aWriteOffReducesTheLotAndPosts() {
            long supplierId = supplier();
            long productId = product().id();
            owner.post("/api/goods-receipts", """
                    {"supplierId": %d,
                     "deliveryNoteNumber": "DN-%d",
                     "receiptDate": "2026-03-05",
                     "lines": [{
                       "productId": %d,
                       "quantity": "6.000000",
                       "unitCost": {"amount": "10.000000", "currency": "EUR"},
                       "location": "INVENTORY"
                     }]}
                    """.formatted(supplierId, next(), productId));

            String lots = owner.get("/api/inventory/lots?productId=" + productId).getBody();
            assertThat(lots).contains("\"6.000000\"");
            long lotId = idOf(lots);

            ResponseEntity<String> writeOff = owner.post("/api/inventory/write-offs", """
                    {"lotId": %d,
                     "quantity": "1.000000",
                     "reason": "DAMAGE",
                     "writeOffDate": "2026-03-06",
                     "note": "Crushed in transit"}
                    """.formatted(lotId));

            assertThat(writeOff.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            // The reason is what makes one write-off account as informative as three would be.
            assertThat(writeOff.getBody()).contains("DAMAGE");
            assertThat(owner.get("/api/inventory/lots/" + lotId).getBody())
                    .contains("\"5.000000\"");
        }

        @Test
        @DisplayName("a write-off with no reason is refused, not defaulted to OTHER")
        void aWriteOffNeedsAReason() {
            long lotId = aLot();

            ResponseEntity<String> response = owner.post("/api/inventory/write-offs", """
                    {"lotId": %d, "quantity": "1.000000", "writeOffDate": "2026-03-06"}
                    """.formatted(lotId));

            assertThat(response.getStatusCode())
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_CONTENT);
        }

        @Test
        @DisplayName("the Damaged Goods ageing query is reachable — phase 8 needs it")
        void damagedGoodsIsQueryable() {
            // Moving a lot to Damaged Goods posts nothing, so without this query the balance sheet
            // would carry worthless stock at full cost indefinitely.
            assertThat(owner.get("/api/inventory/lots?location=DAMAGED_GOODS").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("moving a lot to Damaged Goods posts nothing and is still allowed")
        void movingToDamagedGoodsIsAllowed() {
            long lotId = aLot();

            ResponseEntity<String> moved = owner.post("/api/inventory/lots/" + lotId + "/location",
                    "{\"location\": \"DAMAGED_GOODS\"}");

            assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(owner.get("/api/inventory/lots?location=DAMAGED_GOODS").getBody())
                    .contains("\"id\":" + lotId);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    private ApiClient.Session buyerSession() {
        if (users.findByUsername(BUYER_USERNAME).isEmpty()) {
            RoleView role = roles.create(new NewRole("PURCH_BUYER", "Reads purchase documents"));
            roles.grant(role.id(), Section.PURCHASING, AccessLevel.VIEW);
            users.create(new NewUser(BUYER_USERNAME, "Purchasing Buyer", BUYER_PASSWORD, role.id()));
        }
        return api.logIn(BUYER_USERNAME, BUYER_PASSWORD);
    }

    private static int next() {
        return SEQUENCE.incrementAndGet();
    }

    private long supplier() {
        return suppliers.create(NewSupplier.domestic(
                "PurchIT supplier " + next(), "EL0777%05d".formatted(next()))).id();
    }

    /** A product plus its SKU, which is what assertions key off — line views carry no parent id. */
    private record TestProduct(long id, String sku) {
    }

    private TestProduct product() {
        String sku = "PURCHIT-" + next();
        long id = products.create(new NewProduct(
                sku, null, "PurchIT item", null, ProductType.GOODS,
                unitsOfMeasure.active().getFirst().id(), standardVatClassId(),
                null, null, null, false)).id();
        return new TestProduct(id, sku);
    }

    private long standardVatClassId() {
        return vatClasses.active().getFirst().id();
    }

    private long anInvoice() {
        return idOf(created(owner.post("/api/purchase-invoices", """
                {"supplierId": %d,
                 "supplierInvoiceNumber": "INV-%d",
                 "invoiceDate": "2026-03-01",
                 "lines": [{
                   "type": "INVENTORY",
                   "productId": %d,
                   "quantity": "1.000000",
                   "unitPrice": {"amount": "5.000000", "currency": "EUR"},
                   "vatClassId": %d,
                   "reverseCharge": false
                 }]}
                """.formatted(supplier(), next(), product().id(), standardVatClassId()))));
    }

    private long aLot() {
        long productId = product().id();
        owner.post("/api/goods-receipts", """
                {"supplierId": %d,
                 "deliveryNoteNumber": "DN-%d",
                 "receiptDate": "2026-03-05",
                 "lines": [{
                   "productId": %d,
                   "quantity": "3.000000",
                   "unitCost": {"amount": "10.000000", "currency": "EUR"},
                   "location": "INVENTORY"
                 }]}
                """.formatted(supplier(), next(), productId));
        return idOf(owner.get("/api/inventory/lots?productId=" + productId).getBody());
    }

    private static String created(ResponseEntity<String> response) {
        assertThat(response.getStatusCode())
                .as("expected 201, body was: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** The first {@code "id":N} in a payload. */
    private static long idOf(String json) {
        return numberAfter(json, "\"id\":");
    }

    private static long firstLineIdOf(String json) {
        int lines = json.indexOf("\"lines\"");
        assertThat(lines).as("no lines in %s", json).isGreaterThan(-1);
        return numberAfter(json.substring(lines), "\"id\":");
    }

    private static long numberAfter(String json, String key) {
        int start = json.indexOf(key);
        assertThat(start).as("%s not found in %s", key, json).isGreaterThan(-1);
        start += key.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
