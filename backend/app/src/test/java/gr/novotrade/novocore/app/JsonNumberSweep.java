package gr.novotrade.novocore.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * <strong>No decimal ever crosses the wire as a JSON number.</strong>
 *
 * <p>Step 14's D3 called this the one decision that is hardest to change later, and it is
 * {@code CLAUDE.md} rule 5 at the only layer that faces outward: JSON has no decimal type, so a
 * number literal becomes an IEEE-754 double the moment a browser parses it. {@code 12.505} — the
 * unit cost behind Q45 — is not representable, and a client that received it has already lost the
 * value before any code of ours runs.
 *
 * <p>{@code MasterDataEndpointIT} asserts this for chosen fields on chosen responses. This is the
 * total version: every response body the scenario produces is swept, and any floating-point number
 * anywhere in it fails the request that returned it. Chosen-field assertions cannot cover a field
 * added later, and the fields most likely to be wrong are the ones nobody thought to assert on.
 *
 * <p><strong>An integer is left alone</strong> — ids, counts and line numbers are genuinely
 * integers, and demanding they be strings would be a rule nobody could follow. What is refused is a
 * number with a fractional part, {@code 1.0} included: if a value can have a decimal point it is a
 * decimal, and its wire format has to be one that survives.
 *
 * <p>If this ever fires on something legitimate, that is a finding worth reading rather than an
 * assertion worth weakening.
 */
final class JsonNumberSweep {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonNumberSweep() {
    }

    /**
     * @throws AssertionError naming the request, the location in the document and the value
     */
    static void check(HttpMethod method, String path, String contentType, String body) {
        if (body == null || body.isBlank() || contentType == null
                || !contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
            return;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception malformed) {
            // Not this sweep's business. A body that claims to be JSON and is not will fail
            // whatever assertion the caller was making about it, with a better message than here.
            return;
        }

        List<String> violations = new ArrayList<>();
        walk(root, "$", violations);

        if (!violations.isEmpty()) {
            throw new AssertionError(String.format("""
                    %s %s returned %d floating-point JSON number(s). Money, quantities and unit \
                    costs must cross the wire as strings — a JSON number becomes an IEEE-754 \
                    double in the browser, which is CLAUDE.md rule 5 broken at the boundary.
                      %s
                    Body: %s""",
                    method, path, violations.size(), String.join("\n  ", violations), abbreviate(body)));
        }
    }

    private static void walk(JsonNode node, String where, List<String> violations) {
        if (node.isFloatingPointNumber()) {
            violations.add(where + " = " + node.asText() + " (should be a string)");
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), where + "[" + i + "]", violations);
            }
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                walk(field.getValue(), where + "." + field.getKey(), violations);
            }
        }
    }

    private static String abbreviate(String body) {
        return body.length() <= 600 ? body : body.substring(0, 600) + "… (" + body.length() + " chars)";
    }
}
