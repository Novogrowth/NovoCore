package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.web.json.NovoCoreJsonModule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Building request bodies and reading responses, for the step 15 scenario.
 *
 * <h2>Request bodies are serialised from the real {@code New*} records</h2>
 *
 * <p>Rather than hand-written JSON strings. The scenario builds a {@code NewPurchaseInvoice} — the
 * same record the controller binds to — and this serialises it with {@link NovoCoreJsonModule}, the
 * same module the server deserialises with.
 *
 * <p><strong>The trade-off, stated rather than hidden.</strong> Hand-written JSON would additionally
 * catch a field name mismatch; typed records cannot, because both ends read one definition. That is
 * accepted here for two reasons. A trading narrative fifty documents long, written as raw JSON,
 * would spend its failures on typos rather than on the API's behaviour — and the parser is already
 * covered where it belongs, by {@code MasterDataEndpointIT}, which asserts against raw bytes and
 * proves a JSON number is refused. Step 15's refusal matrix keeps using raw JSON for exactly the
 * cases where the shape itself is what is under test.
 *
 * <p>What is <em>not</em> given up: the request really is serialised, really crosses HTTP, and is
 * really deserialised by the server's own ObjectMapper configuration. A field the server cannot bind
 * still fails.
 */
final class Json {

    /**
     * Configured exactly as the application configures its own — the module is registered as a bean
     * in {@code WebConfiguration}, which is what makes money a string everywhere rather than at the
     * fields somebody remembered.
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new NovoCoreJsonModule())
            .build();

    private Json() {
    }

    /** A request body, from the same record type the controller binds. */
    static String write(Object body) {
        return MAPPER.writeValueAsString(body);
    }

    static JsonNode read(ResponseEntity<String> response) {
        assertThat(response.getBody())
                .as("%s returned no body", response.getStatusCode())
                .isNotNull();
        return MAPPER.readTree(response.getBody());
    }

    /**
     * The {@code id} of a created document, after asserting the creation actually succeeded.
     *
     * <p>The status assertion is here rather than at the call site deliberately: a narrative fifty
     * documents long that checked statuses by hand would eventually not check one, and the failure
     * would surface later as a null id with no indication of which call went wrong.
     */
    static long createdId(ResponseEntity<String> response, String what) {
        assertThat(response.getStatusCode())
                .as("creating %s failed: %s", what, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        JsonNode id = read(response).get("id");
        assertThat(id).as("%s was created without an id in the response — the next call in the "
                + "narrative has nothing to reference", what).isNotNull();
        return id.asLong();
    }

    /** A 2xx response's body, after asserting the call succeeded. */
    static JsonNode ok(ResponseEntity<String> response, String what) {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("%s failed with %s: %s", what, response.getStatusCode(), response.getBody())
                .isTrue();
        return read(response);
    }

    /** The {@code items} array of a list response, which every list is wrapped in (step 14's D6). */
    static List<JsonNode> items(ResponseEntity<String> response, String what) {
        JsonNode body = ok(response, what);
        JsonNode items = body.get("items");
        assertThat(items).as("%s is not wrapped in an items envelope: %s", what, body).isNotNull();
        List<JsonNode> list = new ArrayList<>();
        items.forEach(list::add);
        return list;
    }

    /** The ids in a list response, in the order returned. */
    static List<Long> idsIn(ResponseEntity<String> response, String what) {
        return items(response, what).stream().map(node -> node.get("id").asLong()).toList();
    }

    /** The line ids of a document, which the next document in the narrative has to reference. */
    static List<Long> lineIds(JsonNode document) {
        JsonNode lines = document.get("lines");
        assertThat(lines).as("document has no lines array: %s", document).isNotNull();
        List<Long> ids = new ArrayList<>();
        lines.forEach(line -> ids.add(line.get("id").asLong()));
        return ids;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** An {@code {amount, currency}} object's amount, as the string it crossed the wire as. */
    static String amount(JsonNode node, String field) {
        JsonNode money = node.get(field);
        assertThat(money).as("no field '%s' in %s", field, node).isNotNull();
        if (money.isString()) {
            return money.asString();
        }
        JsonNode amount = money.get("amount");
        assertThat(amount).as("field '%s' is not a money object: %s", field, money).isNotNull();
        return amount.asString();
    }
}
