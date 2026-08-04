package gr.novotrade.novocore.app.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import gr.novotrade.novocore.core.web.Requires;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generates {@code docs/api/openapi.json} from the live handler mapping, and fails on drift.
 *
 * <h2>Why the spec is ours rather than springdoc's</h2>
 *
 * <p>springdoc was tried and rejected on evidence, not preference — see {@link OpenApiSchema}, which
 * records what it produced. The short version: it introspects with Jackson 2 while our serialisers
 * are Jackson 3, so it described {@code Money.amount} as a {@code number} and invented
 * {@code Quantity} as an object with four boolean fields. A generated client would have told the
 * frontend that a money amount is a JavaScript number, which is the one thing this codebase spends
 * the most effort preventing.
 *
 * <p>The machinery to do it properly already existed: {@code RouteCoverage} and
 * {@code EndpointDeclarationCheck} both walk {@link RequestMappingHandlerMapping} already, and this
 * reads the same source. So the denominator is Spring's own view of what is served, and it cannot
 * drift from the routes that really exist.
 *
 * <h2>The drift check is the point, not the file</h2>
 *
 * <p>A generated spec nobody compares against anything achieves nothing. The document is
 * <strong>committed</strong>, and this test regenerates it and fails if it differs — so a backend
 * change that alters the surface fails the build until the new spec is committed, which makes the
 * contract change visible in the diff and reviewable like any other change. The frontend generates
 * its types from a file in the repository and never needs a running backend to build.
 *
 * <p>To accept a change: {@code mvn verify -Dnovocore.openapi.write=true}, then commit the result.
 * That is deliberately an explicit act rather than a silent rewrite during an ordinary build.
 *
 * <h2>What it carries that springdoc could not have known</h2>
 *
 * <p>Each operation records the {@link Section} governing it and the {@link AccessLevel} required,
 * from the route's own {@code @Requires} — so the permission model travels with the contract instead
 * of being rediscovered by a frontend hitting 403s.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=openapi.owner",
            "novocore.bootstrap.owner-password=owner-password-long-enough",
        })
@Import(PostgresTestContainerConfiguration.class)
class OpenApiSpecIT {

    /** Relative to the {@code app} module, which is the working directory for its tests. */
    private static final Path SPEC = Path.of("..", "..", "docs", "api", "openapi.json");

    private static final String WRITE_PROPERTY = "novocore.openapi.write";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            .build();

    @Autowired
    private List<RequestMappingHandlerMapping> handlerMappings;

    /**
     * The real Boot-configured mapper. {@link OpenApiSchema} needs it to answer what Jackson
     * publishes for a response record; a mapper built here would answer whatever it was configured
     * to answer.
     */
    @Autowired
    private tools.jackson.databind.ObjectMapper realMapper;

    @Test
    @DisplayName("the committed OpenAPI spec matches the routes actually served")
    void theSpecMatchesTheSurface() throws IOException {
        // Normalised to LF, not left at whatever the platform uses. Jackson's pretty-printer
        // indents with the SYSTEM line separator, so this file would be CRLF when generated on
        // Windows and LF on the Linux CI runner — and the drift check would then fail on every
        // build that ran somewhere other than where the file was last written, reporting a contract
        // change that had not happened. A committed artefact compared across machines has to have
        // one spelling.
        String generated = (MAPPER.writeValueAsString(buildDocument()) + "\n")
                .replace("\r\n", "\n");

        if (Boolean.getBoolean(WRITE_PROPERTY)) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, generated, StandardCharsets.UTF_8);
            System.out.println("Wrote " + SPEC.toAbsolutePath().normalize());
            return;
        }

        assertThat(Files.exists(SPEC))
                .as("%s does not exist. Generate it with "
                        + "`mvn verify -Dnovocore.openapi.write=true` and commit it.",
                        SPEC.toAbsolutePath().normalize())
                .isTrue();

        assertThat(Files.readString(SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("""
                        The committed API spec no longer describes the routes this application \
                        serves. That is a CONTRACT CHANGE: the frontend generates its types from \
                        this file, so whatever changed here will change there. Regenerate with \
                        `mvn verify -Dnovocore.openapi.write=true`, then commit the result so the \
                        change is visible in the diff and reviewed like any other.""")
                .isEqualTo(generated);
    }

    /**
     * Every {@code Money}, {@code UnitCost}, {@code Quantity} and {@code Rate} is a string.
     *
     * <p><strong>The acceptance test the whole approach was chosen against.</strong> It is stated
     * separately from the drift check because it is a different claim: the drift check says the spec
     * matches the code, and this says the spec is not lying about the one thing that matters most.
     * A generator could satisfy the first while failing this — springdoc did exactly that.
     *
     * <p>It reads the generated document rather than {@code OpenApiSchema}'s source, so it would
     * catch the mapping being changed as well as the wire format drifting away from it.
     */
    @Test
    @DisplayName("no monetary or decimal value is typed as a JSON number anywhere in the spec")
    void moneyIsAlwaysAString() {
        Map<String, Object> document = buildDocument();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> schemas = (Map<String, Map<String, Object>>)
                ((Map<String, Object>) document.get("components")).get("schemas");

        // Money and UnitCost: an object whose amount and currency are both strings.
        for (String name : List.of("Money", "UnitCost")) {
            assertThat(schemas).containsKey(name);
            @SuppressWarnings("unchecked")
            Map<String, Object> properties =
                    (Map<String, Object>) schemas.get(name).get("properties");

            for (String field : List.of("amount", "currency")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> property = (Map<String, Object>) properties.get(field);
                assertThat(property.get("type"))
                        .as("%s.%s must be a string in the spec. A JSON number is parsed as an "
                                + "IEEE-754 double by a JavaScript client and cannot carry an exact "
                                + "decimal amount — Q45 is the standing proof that this is not "
                                + "hypothetical. This is the check springdoc failed, and the reason "
                                + "the spec is generated here instead.", name, field)
                        .isEqualTo("string");
            }
        }

        // Quantity and Rate: a bare string, not an object.
        for (String name : List.of("Quantity", "Rate")) {
            assertThat(schemas).containsKey(name);
            assertThat(schemas.get(name).get("type"))
                    .as("%s crosses the wire as a bare string such as \"3.000000\". springdoc "
                            + "described it as an object with number and boolean fields, which is "
                            + "not a rounding of the truth but a different shape entirely.", name)
                    .isEqualTo("string");
        }

        // And nothing anywhere in the document is typed "number". Every decimal on this surface is
        // one of the four types above; a "number" appearing means a new one escaped untyped.
        List<String> offenders = new ArrayList<>();
        findNumbers(document, "", offenders);
        assertThat(offenders)
                .as("""
                        Schemas typed as a JSON number. Every decimal on this API is a Money, \
                        UnitCost, Quantity or Rate, and all four are strings. A "number" here \
                        means a new decimal reached the surface untyped — wrap it in a value type \
                        rather than letting the spec promise a double.""")
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void findNumbers(Object node, String path, List<String> offenders) {
        if (node instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if ("number".equals(type)) {
                offenders.add(path);
            }
            map.forEach((key, value) -> findNumbers(value, path + "/" + key, offenders));
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                findNumbers(list.get(i), path + "/" + i, offenders);
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Building the document
    // -------------------------------------------------------------------------------------------

    private Map<String, Object> buildDocument() {
        OpenApiSchema schema = new OpenApiSchema(realMapper);
        Map<String, Map<String, Object>> paths = new TreeMap<>();
        Map<String, List<String>> routesByOperationId = new TreeMap<>();

        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                    : mapping.getHandlerMethods().entrySet()) {

                PathPatternsRequestCondition patterns = entry.getKey().getPathPatternsCondition();
                if (patterns == null) {
                    continue;
                }
                for (String pattern : patterns.getPatternValues()) {
                    if (!pattern.startsWith("/api/")) {
                        continue;
                    }
                    for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                        Map<String, Object> operation =
                                operation(entry.getValue(), schema, pattern);
                        routesByOperationId
                                .computeIfAbsent((String) operation.get("operationId"),
                                        key -> new ArrayList<>())
                                .add(method.name() + " " + pattern);
                        paths.computeIfAbsent(pattern, key -> new TreeMap<>())
                                .put(method.name().toLowerCase(java.util.Locale.ROOT), operation);
                    }
                }
            }
        }

        refuseDuplicateOperationIds(routesByOperationId);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "NovoCore API");
        info.put("version", "0.1.0");
        info.put("description",
                "Generated from the live handler mapping by OpenApiSpecIT. Do not edit by hand — "
                        + "regenerate with `mvn verify -Dnovocore.openapi.write=true`.\n\n"
                        + "Every monetary amount, unit cost, quantity and rate is a STRING, never a "
                        + "JSON number. Sending a number where one of these is expected is refused.");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", schema.components());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.1.0");
        document.put("info", info);
        document.put("paths", paths);
        document.put("components", components);
        return document;
    }

    /**
     * <strong>A duplicate {@code operationId} fails the build instead of being written out.</strong>
     *
     * <p>⚠️ Backend queue item 1, and <strong>the half that mattered</strong>. The collision itself
     * was one method name: {@code POST /api/inventory/write-offs} and
     * {@code GET /api/inventory/write-offs/{id}} were both {@code InventoryController.writeOff}, and
     * {@link #operation} derives the id as {@code Controller_method}. OpenAPI requires
     * {@code operationId} to be unique across a document, so what this generator produced was
     * <strong>an invalid spec that no conforming consumer can read</strong> — and it said nothing.
     *
     * <p>It was found by step 16 generating a TypeScript client from it: the output did not compile,
     * twenty duplicate-identifier errors in one file. The frontend then carried a workaround in
     * {@code orval.config.ts} suffixing the HTTP verb, pinned by a test written to fail in both
     * directions. <strong>Both were deleted when this landed</strong> — a workaround outliving its
     * cause is worse than either.
     *
     * <p>This is the same failure mode step 16a's drift check exists to prevent, one level down: a
     * generator that quietly emits something wrong. The remedy is the one this class already applies
     * to an unknown type — {@code OpenApiSchema.schemaFor} throws rather than guessing — and the
     * message names the routes, because "there is a duplicate somewhere" is not actionable.
     *
     * <p>✅ <strong>Component <em>schema</em> names are guarded too, since 8a (2026-08-03).</strong>
     * This method's javadoc used to record that gap as a queue item; it is closed, and the check
     * lives in {@code OpenApiSchema.claim} because that is where a name is claimed. See it for what
     * the measurement found — four collisions rather than the one Q1 recorded, and why 8a would have
     * <em>created</em> the defect rather than merely coinciding with it.
     */
    private static void refuseDuplicateOperationIds(Map<String, List<String>> routesByOperationId) {
        List<String> collisions = routesByOperationId.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " → " + entry.getValue())
                .toList();

        if (!collisions.isEmpty()) {
            throw new IllegalStateException(
                    "The spec would declare one operationId for more than one operation, which "
                            + "OpenAPI forbids and which produces a document no conforming client "
                            + "can read — a generated TypeScript client will not compile. Rename "
                            + "one of the controller methods; the id is Controller_method.\n  "
                            + String.join("\n  ", collisions));
        }
    }

    private Map<String, Object> operation(HandlerMethod handler, OpenApiSchema schema,
            String pattern) {

        Method method = handler.getMethod();
        String context = handler.getBeanType().getSimpleName() + "." + method.getName();

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", handler.getBeanType().getSimpleName() + "_" + method.getName());

        // The permission model travels with the contract. Read from the route's own declaration,
        // which is the thing actually enforced.
        Requires requires = requiresOn(handler);
        if (requires != null) {
            operation.put("x-novocore-section", requires.section().name());
            operation.put("x-novocore-level", requires.level().name());
        } else {
            operation.put("x-novocore-section", null);
            operation.put("x-novocore-level", "AUTHENTICATED_ONLY");
        }

        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> requestBody = null;

        for (Parameter parameter : method.getParameters()) {
            PathVariable path = parameter.getAnnotation(PathVariable.class);
            RequestParam query = parameter.getAnnotation(RequestParam.class);
            RequestBody body = parameter.getAnnotation(RequestBody.class);

            if (path != null) {
                parameters.add(parameter("path", nameOf(path.value(), parameter), true,
                        schema.schemaFor(parameter.getParameterizedType(),
                                OpenApiSchema.Direction.REQUEST, context)));
            } else if (query != null) {
                parameters.add(parameter("query", nameOf(query.value(), parameter), query.required(),
                        schema.schemaFor(parameter.getParameterizedType(),
                                OpenApiSchema.Direction.REQUEST, context)));
            } else if (body != null) {
                requestBody = content(
                        schema.schemaFor(parameter.getParameterizedType(),
                                OpenApiSchema.Direction.REQUEST, context), true);
            }
        }

        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }
        if (requestBody != null) {
            operation.put("requestBody", requestBody);
        }
        operation.put("responses", responses(method, schema, context));
        return operation;
    }

    private static Map<String, Object> responses(Method method, OpenApiSchema schema,
            String context) {

        ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        HttpStatus success = status == null ? HttpStatus.OK : status.value();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", success.getReasonPhrase());

        if (!void.class.equals(method.getReturnType())) {
            Map<String, Object> body = schema.schemaFor(method.getGenericReturnType(),
                    OpenApiSchema.Direction.RESPONSE, context);
            if (body != null) {
                response.put("content", contentOf(body));
            }
        }

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put(String.valueOf(success.value()), response);
        // Every route can answer these; stated once here rather than per operation. The body shape
        // is RFC 7807 throughout, which WebExceptionHandler and step 15's defects 5-9 settled.
        responses.put("4XX", problem("The request was refused. RFC 7807 body with a 'detail' "
                + "explaining why, except for permission refusals, which say nothing on purpose."));
        return responses;
    }

    private static Map<String, Object> problem(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "integer"));
        properties.put("title", Map.of("type", "string"));
        properties.put("detail", Map.of("type", "string"));
        schema.put("properties", properties);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", description);
        response.put("content", Map.of("application/problem+json", Map.of("schema", schema)));
        return response;
    }

    private static Map<String, Object> parameter(String in, String name, boolean required,
            Map<String, Object> schema) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("name", name);
        parameter.put("in", in);
        parameter.put("required", required);
        parameter.put("schema", schema);
        return parameter;
    }

    private static Map<String, Object> content(Map<String, Object> schema, boolean required) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("required", required);
        body.put("content", contentOf(schema));
        return body;
    }

    private static Map<String, Object> contentOf(Map<String, Object> schema) {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("schema", schema);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("application/json", media);
        return content;
    }

    private static String nameOf(String declared, Parameter parameter) {
        return declared == null || declared.isBlank() ? parameter.getName() : declared;
    }

    /** Resolved the same way the interceptor resolves it: method first, then the controller. */
    private static Requires requiresOn(HandlerMethod handler) {
        Requires onMethod = handler.getMethodAnnotation(Requires.class);
        return onMethod != null ? onMethod : handler.getBeanType().getAnnotation(Requires.class);
    }
}
