package gr.novotrade.novocore.app.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * <strong>The properties Jackson writes must equal the properties the committed spec documents.</strong>
 *
 * <h2>What this exists to catch, and why nothing else could</h2>
 *
 * <p>R1a shipped a derived accessor on {@code AadeInvoiceTypeView} and
 * {@code GET /api/aade-invoice-types} answered {@code 500} for the whole codification. That was the
 * loud half. <strong>The quiet half is what this rule is for:</strong> an accessor that merely
 * <em>returns</em> a value ships an undocumented field on every response, absent from the generated
 * TypeScript, with nothing anywhere to say so. Measured on 2026-08-04, before W1: <strong>32 schemas
 * on this surface wrote 66 properties the document did not mention.</strong> Nothing failed, because
 * nothing sets {@code additionalProperties: false}.
 *
 * <h2>Two sources, not one read twice</h2>
 *
 * <p>The documented side is read from the <strong>committed</strong> {@code docs/api/openapi.json},
 * not from a document generated here. The written side is asked of the <strong>real
 * Boot-configured {@link ObjectMapper} bean</strong>. So a bug in {@link OpenApiSchema} cannot make
 * both sides agree — which it could if this test generated the document itself and compared it
 * against the generator's own idea of Jackson. {@code CLAUDE.md}: two sources that agree is
 * evidence; one source read twice is not.
 *
 * <h2>Scope, stated as a sentence rather than an exemption list</h2>
 *
 * <p>Only records are checked, and only those {@link OpenApiSchema} builds through
 * {@code recordSchema}. {@code Money}, {@code UnitCost}, {@code Quantity} and {@code Rate} are
 * matched by class before they reach it and carry hand-written schemas, because
 * {@code NovoCoreJsonModule} replaces their serialisers — Jackson does not bean-introspect them and
 * would report no properties at all. Eleven {@code is*} accessors live on those four and none is on
 * the wire.
 *
 * <p>Nothing else needs exempting, and that was measured rather than assumed:
 * {@code equals}/{@code hashCode}/{@code toString} are not bean getters, static factories are
 * static, a compact constructor is not a method, and every {@code …IfAny()} returns
 * {@code Optional} — which Jackson does not publish. Of <strong>222</strong> non-component public
 * no-arg accessors on this surface, Jackson publishes <strong>66</strong>.
 *
 * <h2>⚠️ This does NOT subsume {@code AadeInvoiceTypeIT.theViewHasNoDerivedAccessorThatCanThrow}</h2>
 *
 * <p>That test is narrower and stricter on purpose, and it guards something this one cannot see: an
 * accessor that <strong>throws</strong>. Under W1 a throwing bean getter is now <em>documented</em>
 * as a property — and still answers {@code 500} on every row. A documented field is not a working
 * field. Keep both.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=contract.owner",
            "novocore.bootstrap.owner-password=owner-password-long-enough",
        })
@Import(PostgresTestContainerConfiguration.class)
class SerialisedRecordContractIT {

    /** Relative to the {@code app} module, which is the working directory for its tests. */
    private static final Path SPEC = Path.of("..", "..", "docs", "api", "openapi.json");

    /**
     * ⚠️ <strong>A POSITIVE CONTROL, and the reason it is a hard-coded list.</strong>
     *
     * <p>{@link #noRecordReachedFromBothDirectionsCarriesDerivedProperties} asserts that nothing in
     * the both-directions set has derived properties. Today <em>nothing does</em> — so that
     * assertion would pass just as happily against a broken walk, a typo, or a set that came back
     * empty because the traversal never ran. <strong>An empty result would read as compliance.</strong>
     *
     * <p>So the set itself is pinned, non-empty. This is {@code DocumentReferenceGraphIT}'s shape,
     * applied to the same hazard: it is the negative-control requirement arriving from the direction
     * where the expected answer is emptiness.
     *
     * <p>Measured 2026-08-04. <strong>When this list changes, that is a real event</strong> — a
     * record has started crossing the wire in both directions — and the failure message says what to
     * do about it.
     */
    private static final Set<String> RECORDS_REACHED_FROM_BOTH_DIRECTIONS = Set.of("OpenItemRef");

    @Autowired
    private List<RequestMappingHandlerMapping> handlerMappings;

    /** The real bean. A mapper built here would answer whatever it was configured to answer. */
    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("every response schema documents exactly what Jackson writes — no undocumented "
            + "derived properties, and none documented that Jackson would not write")
    void theSpecDocumentsExactlyWhatJacksonWrites() throws IOException {
        Surface surface = walk();
        Map<String, Map<String, Object>> documented = documentedSchemas();

        List<String> undocumented = new ArrayList<>();
        List<String> phantom = new ArrayList<>();

        for (Map.Entry<String, Class<?>> owned : new TreeMap<>(surface.schema.owners()).entrySet()) {
            String name = owned.getKey();
            Class<?> type = owned.getValue();
            if (!type.isRecord()
                    || !surface.responses.contains(name)
                    || !surface.schema.builtByRecordSchema().contains(name)) {
                continue;
            }
            Map<String, Object> schema = documented.get(name);
            if (schema == null) {
                continue;
            }
            Set<String> spec = propertyNames(schema);
            Set<String> wire = surface.schema.jacksonPropertiesOf(type).keySet();

            for (String written : wire) {
                if (!spec.contains(written)) {
                    undocumented.add(name + "." + written + "  (" + type.getName() + ")");
                }
            }
            for (String described : spec) {
                if (!wire.contains(described)) {
                    phantom.add(name + "." + described + "  (" + type.getName() + ")");
                }
            }
        }

        assertThat(undocumented)
                .as("""
                        Jackson writes these properties and the committed spec does not mention \
                        them. Nothing fails today — no schema sets additionalProperties:false, so \
                        the field simply arrives and the generated TypeScript has no name for it — \
                        which is exactly why this needs a build failure rather than attention. \
                        Two honest fixes: DELETE the accessor if nothing needs it, or regenerate \
                        the spec with `mvn verify -Dnovocore.openapi.write=true` so the contract \
                        says what the wire does. Jackson publishes BEAN GETTERS — isXxx() \
                        returning boolean, getXxx() — not every no-arg accessor; a plain foo() or \
                        an Optional-returning fooIfAny() is invisible to it.""")
                .isEmpty();

        assertThat(phantom)
                .as("""
                        The committed spec promises these properties and Jackson does not write \
                        them, so every client dereferencing one reads undefined. This is the \
                        dangerous direction: an incomplete contract is still true, a wrong one is \
                        worse than none.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no request schema documents a derived property — a request record is never "
            + "serialised, so a derived property there describes a write that never happens")
    void noRequestSchemaDocumentsADerivedProperty() throws IOException {
        Surface surface = walk();
        Map<String, Map<String, Object>> documented = documentedSchemas();

        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, Class<?>> owned : new TreeMap<>(surface.schema.owners()).entrySet()) {
            String name = owned.getKey();
            Class<?> type = owned.getValue();
            if (!type.isRecord()
                    || !surface.requests.contains(name)
                    || surface.responses.contains(name)
                    || !surface.schema.builtByRecordSchema().contains(name)) {
                continue;
            }
            Map<String, Object> schema = documented.get(name);
            if (schema == null) {
                continue;
            }
            Set<String> componentNames = componentNames(type);
            for (String described : propertyNames(schema)) {
                if (!componentNames.contains(described)) {
                    offenders.add(name + "." + described + "  (" + type.getName() + ")");
                }
            }
        }

        assertThat(offenders)
                .as("""
                        A request schema documents a property that is not a record component. \
                        Jackson DESERIALISES a request through the canonical constructor, which \
                        sees exactly the components — a request record is never serialised at all, \
                        so a derived property here describes a write that never happens, and a \
                        generated client would be told to compute and send a value the server \
                        discards. This is not a policy about what clients ought to send: it is the \
                        same rule as the response check, asked of the other mechanism.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠️ no record reached from BOTH directions carries a derived property — with the "
            + "both-directions set pinned, so an empty result cannot read as compliance")
    void noRecordReachedFromBothDirectionsCarriesDerivedProperties() {
        Surface surface = walk();
        SortedSet<String> both = surface.schema.recordsReachedFromBothDirections();

        // POSITIVE CONTROL FIRST. Without it, the assertion below passes against a walk that found
        // nothing at all — and "no offenders" would be indistinguishable from "this test measures
        // nothing". The expected answer here is NOT emptiness, which is the whole point.
        assertThat(both)
                .as("""
                        The set of records reached as BOTH a request and a response has changed. \
                        This list is a positive control: it is pinned non-empty so that the \
                        derived-property assertion below cannot pass by measuring nothing. If a \
                        record has legitimately started crossing the wire in both directions, add \
                        it here — and then make sure it has no derived properties, because \
                        OpenApiSchema.recordSchema describes such a record in whichever direction \
                        reached it first, and Map iteration order would decide the document.""")
                .containsExactlyInAnyOrderElementsOf(RECORDS_REACHED_FROM_BOTH_DIRECTIONS)
                .isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (String name : both) {
            Class<?> type = surface.schema.owners().get(name);
            Set<String> componentNames = componentNames(type);
            for (String written : surface.schema.jacksonPropertiesOf(type).keySet()) {
                if (!componentNames.contains(written)) {
                    offenders.add(name + "." + written + "  (" + type.getName() + ")");
                }
            }
        }

        assertThat(offenders)
                .as("""
                        This record crosses the wire in BOTH directions and has a derived \
                        property, which cannot be described correctly either way: documenting it \
                        lies to the request side, omitting it lies to the response side, and \
                        OpenApiSchema takes whichever direction reached it first — so the \
                        committed document would depend on iteration order. Delete the accessor, \
                        or split the record into a request type and a response type. \
                        OpenItemRef.isCustomerSide() was the first instance and was deleted, \
                        because it had zero references anywhere in compiled code.""")
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------------

    /** What the walk found: the populated schema, and which names each direction reached. */
    private record Surface(OpenApiSchema schema, Set<String> requests, Set<String> responses) {}

    /**
     * The same traversal {@code OpenApiSpecIT} performs, reduced to what decides a schema's shape.
     *
     * <p>Deliberately not shared with that class. This test's value is that it reaches the same
     * types by the same route and then asks a <em>different</em> source what they look like; a
     * shared builder would tempt someone to compare the generator against itself.
     */
    private Surface walk() {
        OpenApiSchema schema = new OpenApiSchema(mapper);
        OpenApiSchema requestsOnly = new OpenApiSchema(mapper);
        OpenApiSchema responsesOnly = new OpenApiSchema(mapper);

        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                    : mapping.getHandlerMethods().entrySet()) {

                PathPatternsRequestCondition patterns = entry.getKey().getPathPatternsCondition();
                if (patterns == null || patterns.getPatternValues().stream()
                        .noneMatch(pattern -> pattern.startsWith("/api/"))) {
                    continue;
                }
                HandlerMethod handler = entry.getValue();
                Method method = handler.getMethod();
                String context = handler.getBeanType().getSimpleName() + "." + method.getName();

                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getAnnotation(RequestBody.class) != null
                            || parameter.getAnnotation(PathVariable.class) != null
                            || parameter.getAnnotation(RequestParam.class) != null) {
                        schema.schemaFor(parameter.getParameterizedType(),
                                OpenApiSchema.Direction.REQUEST, context);
                        requestsOnly.schemaFor(parameter.getParameterizedType(),
                                OpenApiSchema.Direction.REQUEST, context);
                    }
                }
                if (!void.class.equals(method.getReturnType())) {
                    schema.schemaFor(method.getGenericReturnType(),
                            OpenApiSchema.Direction.RESPONSE, context);
                    responsesOnly.schemaFor(method.getGenericReturnType(),
                            OpenApiSchema.Direction.RESPONSE, context);
                }
            }
        }
        return new Surface(schema,
                new TreeSet<>(requestsOnly.owners().keySet()),
                new TreeSet<>(responsesOnly.owners().keySet()));
    }

    /** The COMMITTED document — the artefact clients are generated from, not one built here. */
    private static Map<String, Map<String, Object>> documentedSchemas() throws IOException {
        String json = Files.readString(SPEC, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> document = JsonMapper.builder().build().readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> schemas =
                (Map<String, Map<String, Object>>) components.get("schemas");
        return schemas;
    }

    private static Set<String> propertyNames(Map<String, Object> schema) {
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return properties == null ? Set.of() : new LinkedHashSet<>(properties.keySet());
    }

    private static Set<String> componentNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (RecordComponent component : type.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }
}
