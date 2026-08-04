package gr.novotrade.novocore.app.openapi;

import gr.novotrade.novocore.core.api.shared.ConditionallyMandatory;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.Rate;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns a Java type into an OpenAPI schema, <strong>reading this codebase's own serialisers</strong>.
 *
 * <h2>Why this is written rather than taken</h2>
 *
 * <p>springdoc was tried first and its output was kept as evidence rather than as a near miss. It
 * introspects models with <em>Jackson 2</em>'s {@code ObjectMapper}, while {@code NovoCoreJsonModule}
 * is a Jackson <em>3</em> module — so it cannot see a single one of our serialisers and falls back to
 * reflecting Java accessors. What it produced for the four types {@code CLAUDE.md} rule 5 exists to
 * protect:
 *
 * <pre>
 *   Money    {"amount": {"type":"number"},
 *             "currency": {"currencyCode":…, "displayName":…, "symbol":…,
 *                          "defaultFractionDigits":…, "numericCode":…},
 *             "zero": bool, "negative": bool, …}
 *   Quantity {"value": {"type":"number"}, "zero": bool, "negative": bool, "positive": bool}
 *   Rate     {"percent": {"type":"number"}, "zero": bool}
 * </pre>
 *
 * <p>Against the actual wire format:
 *
 * <pre>
 *   Money    {"amount": "12.50", "currency": "EUR"}
 *   Quantity "3.000000"
 *   Rate     "24.000000"
 * </pre>
 *
 * <p>Not a rounding of the truth — a different structure for three of the four, plus
 * {@code amount: number}, which is the exact claim step 15a spent a commit removing from this
 * codebase. A generated TypeScript client would have told the frontend a money amount is a
 * {@code number}. So the generator reads the serialisers instead, and here that means four entries
 * written by hand next to the module they mirror.
 *
 * <h2>An unknown type fails the build</h2>
 *
 * <p>{@link #schemaFor} throws on anything it does not recognise rather than emitting a permissive
 * {@code {}} or guessing at {@code object}. A spec that quietly describes a new type wrongly is the
 * failure just demonstrated; a spec that refuses to be generated until somebody says what the type
 * looks like is the one worth having. Adding a shape is a deliberate edit here.
 *
 * <h2>W1 — what "describe what Jackson writes" is scoped to</h2>
 *
 * <p>{@code SerialisedRecordContractIT} holds this document to what Jackson actually publishes.
 * <strong>That rule is scoped to records built by {@link #recordSchema}, and the reason is a
 * sentence rather than an exemption list:</strong> {@code Money}, {@code UnitCost},
 * {@code Quantity} and {@code Rate} are matched by class in {@link #classSchema} <em>before</em>
 * they could reach it, and their schemas are the four hand-written ones above. {@code
 * NovoCoreJsonModule} replaces their serialisers, so Jackson never bean-introspects them and
 * reports no object properties for them at all — asking it what they publish would answer
 * "nothing", which is exactly the springdoc failure this class exists to avoid, arriving from the
 * opposite side. Eleven {@code is*} accessors live on those four types and none of them is on the
 * wire.
 */
final class OpenApiSchema {

    /**
     * Which way a record crosses the wire, because Jackson does two different things to it.
     *
     * <p>See {@link #recordSchema} for why this is one rule and not two behaviours.
     */
    enum Direction {
        /** A {@code @RequestBody}. Jackson <em>deserialises</em> it. */
        REQUEST,
        /** A handler's return type. Jackson <em>serialises</em> it. */
        RESPONSE
    }

    /**
     * The real Boot-configured mapper — <strong>the only thing that can answer what Jackson writes.</strong>
     *
     * <p>Not a mapper built here. A mapper constructed in a test is configured by the test, so asking
     * it what the application publishes is {@code CLAUDE.md}'s <em>verification that answers its own
     * request</em>: it returns whatever it was told to return.
     */
    private final tools.jackson.databind.ObjectMapper mapper;

    OpenApiSchema(tools.jackson.databind.ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Named component schemas, by schema name. Sorted so the written document is stable. */
    private final Map<String, Map<String, Object>> components = new TreeMap<>();

    /** Which type claimed each schema name, so a second claimant fails rather than being ignored. */
    private final Map<String, Class<?>> owners = new HashMap<>();

    /** Which directions each schema name was reached from. See {@code recordsReachedFromBothDirections}. */
    private final Map<String, java.util.EnumSet<Direction>> directions = new TreeMap<>();

    Map<String, Map<String, Object>> components() {
        return components;
    }

    /**
     * Which Java type each published schema name describes.
     *
     * <p>Exposed for W1: {@code SerialisedRecordContractIT} compares what Jackson writes against what
     * the committed document says, and needs the <em>classes</em> the walk reached. Rebuilding that
     * set would mean reimplementing the generic-type recursion below and getting a different, quieter
     * answer.
     */
    Map<String, Class<?>> owners() {
        return owners;
    }

    /**
     * The schema names {@link #recordSchema} built — <strong>the rule's scope, as a fact rather than
     * a list.</strong>
     *
     * <p>{@code Money}, {@code UnitCost}, {@code Quantity} and {@code Rate} are records and do appear
     * in {@link #owners()}, but {@link #classSchema} matches them by class first and their schemas
     * are the four hand-written ones at the top of this file. Asking Jackson what they publish
     * answers "nothing" — {@code NovoCoreJsonModule} replaces their serialisers — so a rule that did
     * not exclude them would report {@code Money.amount} and {@code Money.currency} as promises
     * nothing keeps. <strong>It did, on its first run.</strong> This is that scope expressed as
     * something the generator knows rather than something a test hard-codes.
     */
    java.util.Set<String> builtByRecordSchema() {
        return java.util.Set.copyOf(directions.keySet());
    }

    /**
     * Schema names reached as <strong>both</strong> a request and a response.
     *
     * <p>Such a record is described once, and {@link #recordSchema} adds derived properties only in
     * the {@code RESPONSE} direction — so whichever direction reached it first would decide the
     * document, and {@code Map} iteration order would decide that. {@code SerialisedRecordContractIT}
     * therefore refuses any record in this set that has derived properties, and <strong>pins the set
     * itself</strong> so that "nothing here" is a measurement rather than an absence.
     */
    java.util.SortedSet<String> recordsReachedFromBothDirections() {
        java.util.SortedSet<String> both = new java.util.TreeSet<>();
        directions.forEach((name, reached) -> {
            if (reached.size() == 2 && owners.get(name) != null && owners.get(name).isRecord()) {
                both.add(name);
            }
        });
        return both;
    }

    /**
     * What Jackson would publish for this type, <strong>and at what type</strong>.
     *
     * <p>⚠️ <strong>The type comes from Jackson too, never from a reflective lookup by name.</strong>
     * That was the first implementation and it made the generator <em>non-deterministic</em>:
     * {@code CustomerView} has both {@code isSystemRecord():boolean} and
     * {@code systemRecord():Optional<CustomerSystemKey>}, which map to the one published name
     * {@code systemRecord}, and a name-based lookup picks between them in
     * {@code Class.getMethods()} order — which the JVM does not specify. Two runs of the generator
     * produced two different documents and {@code OpenApiSpecIT}'s drift check caught it. Jackson
     * knows which member it will write; nothing else does.
     */
    Map<String, Class<?>> jacksonPropertiesOf(Class<?> raw) {
        Map<String, Class<?>> published = new LinkedHashMap<>();
        mapper.acceptJsonFormatVisitor(raw,
                new tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper.Base() {
                    @Override
                    public tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor
                            expectObjectFormat(tools.jackson.databind.JavaType visited) {
                        return new tools.jackson.databind.jsonFormatVisitors
                                .JsonObjectFormatVisitor.Base(getContext()) {
                            @Override
                            public void property(tools.jackson.databind.BeanProperty property) {
                                published.put(property.getName(), property.getType().getRawClass());
                            }

                            @Override
                            public void optionalProperty(tools.jackson.databind.BeanProperty property) {
                                published.put(property.getName(), property.getType().getRawClass());
                            }
                        };
                    }
                });
        return published;
    }

    // -----------------------------------------------------------------------------------------
    // The four value types — the whole reason this class exists
    // -----------------------------------------------------------------------------------------

    /**
     * {@code Money} and {@code UnitCost}: an object of two <strong>strings</strong>.
     *
     * <p>The amount is a string because JSON has no decimal type and {@code JSON.parse} produces an
     * IEEE-754 double — {@code 12.505} is not representable in one, and Q45 is the standing proof
     * that sub-cent arithmetic here is not hypothetical. The currency is carried and never assumed
     * (ADR 0005).
     */
    private static Map<String, Object> amountSchema(String what, String example, String scale) {
        Map<String, Object> amount = ordered();
        amount.put("type", "string");
        amount.put("pattern", "^-?\\d+(\\.\\d+)?$");
        amount.put("example", example);
        amount.put("description",
                "A decimal " + what + " at " + scale + ", as a STRING and never a JSON number. A "
                        + "JSON number is parsed as an IEEE-754 double by a JavaScript client and "
                        + "cannot carry this value exactly. The backend refuses a number here.");

        Map<String, Object> currency = ordered();
        currency.put("type", "string");
        currency.put("pattern", "^[A-Z]{3}$");
        currency.put("example", "EUR");
        currency.put("description", "ISO 4217 code. Never defaulted, never omitted.");

        Map<String, Object> properties = ordered();
        properties.put("amount", amount);
        properties.put("currency", currency);

        Map<String, Object> schema = ordered();
        schema.put("type", "object");
        schema.put("required", List.of("amount", "currency"));
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** {@code Quantity} and {@code Rate}: a bare string, for the same reason. */
    private static Map<String, Object> bareDecimalSchema(String what, String example) {
        Map<String, Object> schema = ordered();
        schema.put("type", "string");
        schema.put("pattern", "^-?\\d+(\\.\\d+)?$");
        schema.put("example", example);
        schema.put("description",
                "A " + what + " at six decimals, as a STRING and never a JSON number — see Money.");
        return schema;
    }

    // -----------------------------------------------------------------------------------------

    Map<String, Object> schemaFor(Type type, Direction direction, String context) {
        return schemaFor(type, Map.of(), direction, context);
    }

    private Map<String, Object> schemaFor(Type type, Map<String, Type> substitutions,
            Direction direction, String context) {

        if (type instanceof TypeVariable<?> variable) {
            Type resolved = substitutions.get(variable.getName());
            if (resolved == null) {
                throw new IllegalStateException("Unresolved type variable " + variable.getName()
                        + " in " + context);
            }
            return schemaFor(resolved, substitutions, direction, context);
        }

        if (type instanceof ParameterizedType parameterized) {
            return parameterizedSchema(parameterized, substitutions, direction, context);
        }

        if (type instanceof Class<?> raw) {
            return classSchema(raw, direction, context);
        }

        throw new IllegalStateException(
                "No OpenAPI schema for " + type + " (" + context + "). Add one to OpenApiSchema "
                        + "rather than letting the spec describe it wrongly.");
    }

    private Map<String, Object> parameterizedSchema(ParameterizedType type,
            Map<String, Type> substitutions, Direction direction, String context) {

        Class<?> raw = (Class<?>) type.getRawType();
        Type[] arguments = type.getActualTypeArguments();

        if (Collection.class.isAssignableFrom(raw)) {
            Map<String, Object> schema = ordered();
            schema.put("type", "array");
            schema.put("items", schemaFor(arguments[0], substitutions, direction, context));
            if (Set.class.isAssignableFrom(raw)) {
                schema.put("uniqueItems", true);
            }
            return schema;
        }
        if (Optional.class.equals(raw)) {
            return schemaFor(arguments[0], substitutions, direction, context);
        }
        if ("org.springframework.http.ResponseEntity".equals(raw.getName())) {
            // A handler that sets headers itself — the attachment download does, for
            // Content-Disposition. The body is the payload; the envelope is transport.
            return schemaFor(arguments[0], substitutions, direction, context);
        }
        if (Map.class.isAssignableFrom(raw)) {
            Map<String, Object> schema = ordered();
            schema.put("type", "object");
            schema.put("additionalProperties", schemaFor(arguments[1], substitutions, direction, context));
            return schema;
        }
        if (raw.isRecord()) {
            // A generic envelope such as ListResponse<AccountGroupView>. Registered under a name
            // carrying its argument, so two instantiations do not collide on one schema.
            Map<String, Type> resolved = new HashMap<>();
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            for (int i = 0; i < parameters.length; i++) {
                resolved.put(parameters[i].getName(), arguments[i]);
            }
            String name = raw.getSimpleName() + "_" + simpleNameOf(arguments[0]);
            return recordSchema(raw, name, resolved, direction, context);
        }

        throw new IllegalStateException(
                "No OpenAPI schema for parameterized " + raw.getName() + " (" + context + ")");
    }

    private Map<String, Object> classSchema(Class<?> raw, Direction direction, String context) {
        if (Money.class.equals(raw)) {
            claim("Money", raw, context);
            return register("Money", () -> amountSchema("amount", "12.50", "two decimals"));
        }
        if (UnitCost.class.equals(raw)) {
            claim("UnitCost", raw, context);
            return register("UnitCost", () -> amountSchema("unit cost", "12.505000", "six decimals"));
        }
        if (Quantity.class.equals(raw)) {
            claim("Quantity", raw, context);
            return register("Quantity", () -> bareDecimalSchema("quantity", "3.000000"));
        }
        if (Rate.class.equals(raw)) {
            claim("Rate", raw, context);
            return register("Rate", () -> bareDecimalSchema("percentage rate", "24.000000"));
        }

        if (String.class.equals(raw) || CharSequence.class.equals(raw)) {
            return type("string");
        }
        if (boolean.class.equals(raw) || Boolean.class.equals(raw)) {
            return type("boolean");
        }
        if (long.class.equals(raw) || Long.class.equals(raw)) {
            Map<String, Object> schema = type("integer");
            schema.put("format", "int64");
            return schema;
        }
        if (int.class.equals(raw) || Integer.class.equals(raw)) {
            Map<String, Object> schema = type("integer");
            schema.put("format", "int32");
            return schema;
        }
        if (LocalDate.class.equals(raw)) {
            Map<String, Object> schema = type("string");
            schema.put("format", "date");
            return schema;
        }
        if (Instant.class.equals(raw)) {
            Map<String, Object> schema = type("string");
            schema.put("format", "date-time");
            return schema;
        }
        if (byte[].class.equals(raw)
                || "org.springframework.core.io.Resource".equals(raw.getName())) {
            // The attachment download. Bytes, not JSON — the only route on this surface that is
            // not application/json, and it is worth the spec saying so rather than implying a body
            // a client would try to parse.
            Map<String, Object> schema = type("string");
            schema.put("format", "binary");
            return schema;
        }
        if (BigDecimal.class.equals(raw)) {
            // Deliberately refused rather than mapped. Every decimal that reaches the wire in this
            // system is a Money, UnitCost, Quantity or Rate; a bare BigDecimal escaping onto the
            // surface is the defect step 15a's Rate type was created to fix, and the spec should
            // fail rather than describe it.
            throw new IllegalStateException(
                    "A bare BigDecimal reached the API surface (" + context + "). Every decimal on "
                            + "this wire is a Money, UnitCost, Quantity or Rate — see the Rate "
                            + "value type, added in step 15a for exactly this. Wrap it.");
        }
        if (raw.isEnum()) {
            claim(raw.getSimpleName(), raw, context);
            return register(raw.getSimpleName(), () -> {
                Map<String, Object> schema = type("string");
                schema.put("enum", java.util.Arrays.stream(raw.getEnumConstants())
                        .map(constant -> ((Enum<?>) constant).name()).toList());
                return schema;
            });
        }
        if (raw.isRecord()) {
            return recordSchema(raw, raw.getSimpleName(), Map.of(), direction, context);
        }
        if (void.class.equals(raw) || Void.class.equals(raw)) {
            return null;
        }

        throw new IllegalStateException(
                "No OpenAPI schema for " + raw.getName() + " (" + context + "). Add one to "
                        + "OpenApiSchema rather than letting the spec describe it wrongly.");
    }

    /**
     * A record becomes an object whose properties are its <strong>components</strong>, and its
     * <strong>primitive</strong> components are marked required.
     *
     * <p>Components, not accessors — that is what Jackson serialises for a record, and reading
     * accessors instead is precisely how springdoc invented {@code zero}, {@code negative} and
     * {@code positive} as fields of a quantity.
     *
     * <h2>Why a primitive is required, and a reference type is not</h2>
     *
     * <p>A reference type is not marked required, and that is correct for the reason this javadoc
     * used to give for marking <em>nothing</em>: {@code application.yml} sets
     * {@code default-property-inclusion: non_null}, so any null-valued component is simply absent
     * from the body — a client reads a missing key as "not set", and for a field withheld by
     * permission, as "not set, and named in {@code hiddenFields}".
     *
     * <p><strong>That argument is about responses, and this method also builds every request
     * schema.</strong> Applying it in both directions published a contract saying no request body
     * has a mandatory field, and it was not true: a primitive component cannot be null, so Jackson
     * hands an <em>absent</em> creator property to the canonical constructor as {@code null} and
     * {@code FAIL_ON_NULL_FOR_PRIMITIVES} — on, in this application — refuses the body before any
     * handler runs, with a message naming no field. That broke product creation for every user
     * ({@code NewProduct.serialTracked}) and would have broken account creation the same way
     * ({@code NewUser.roleId}); both were found by a client written correctly against this document.
     *
     * <p><strong>The rule is accurate in both directions, which is why it is one rule.</strong> On a
     * request a primitive is mandatory, as above. On a response it is always present: it cannot be
     * null, so {@code non_null} inclusion cannot drop it, nothing here declares {@code @JsonInclude},
     * and the two mechanisms that <em>do</em> withhold data both leave primitives alone —
     * {@code ProductView.redactedFor} nulls three reference-typed fields
     * ({@code Long supplierId}, {@code String supplierSku}, {@code UnitCost lastPurchasePrice}), and
     * {@code SettingView} substitutes a masked {@code String} for a secret's value. Swept across all
     * 53 response records carrying a primitive, with no exception.
     *
     * <h2>{@code @Mandatory} is the other half, and it is declared rather than inferred</h2>
     *
     * <p>A reference-typed component required by a compact constructor ({@code Required.field},
     * {@code Required.text}, {@code Objects.requireNonNull}) is mandatory in exactly the same sense
     * and <strong>invisible to reflection</strong>, because reflection cannot see inside a
     * constructor body. Until 8a (2026-08-03) this method deliberately did not guess at them, and the
     * spec described 339 always-present components as optional.
     *
     * <p>{@link Mandatory} closes it by declaring what cannot be inferred, and
     * {@code MandatoryDeclarationRulesTest} reads the canonical constructor's bytecode to fail the
     * build in both directions — so the declaration cannot drift from the guard. The two rules
     * together are the {@code required} list: a primitive by inference, a reference type by
     * declaration.
     *
     * <p>⚠️ <strong>What is still not declared:</strong> a component whose requirement is
     * <em>conditional</em> carries {@link ConditionallyMandatory} instead and is deliberately left
     * out — {@code NewPurchaseInvoiceLine} has five such fields of which at most three can ever be
     * present, and no {@code required} list can say that. And a component made mandatory by an inline
     * {@code if (x == null) throw} is invisible to the cross-check, so it is not declared either.
     * Both leave the list incomplete rather than wrong, which is the right side of that trade.
     *
     * <h2>W1 — a RESPONSE also carries whatever Jackson derives, and a REQUEST does not</h2>
     *
     * <p>The javadoc above says "components, not accessors", and until W1 (2026-08-04) that was
     * applied in both directions. It is right in one of them. <strong>Jackson publishes bean
     * getters</strong> — {@code isXxx()} returning boolean, {@code getXxx()} — <strong>as well as
     * record components</strong>, so 32 schemas on this surface wrote 66 properties this document
     * did not mention. Nothing broke, because nothing sets {@code additionalProperties: false} and
     * the generated TypeScript simply lacked the fields; the contract was merely lying about 32
     * schemas, which is the half nothing would ever have reported.
     *
     * <h3>⚠️ Why this is ONE rule and not two behaviours — do not collapse it back</h3>
     *
     * <p>The rule is <em>describe what Jackson actually does with this record</em>. Jackson does two
     * genuinely different things:
     *
     * <ul>
     *   <li><strong>A response is SERIALISED.</strong> Jackson asks every bean getter and writes the
     *       answer, so a derived property is on the wire and the document must say so.
     *   <li><strong>A request is DESERIALISED</strong> — through the canonical constructor, which
     *       sees exactly the record components. <strong>A request record is never serialised at
     *       all.</strong> So the seven derived properties on {@code NewSalesInvoiceLine},
     *       {@code NewPurchaseInvoiceLine}, {@code NewGoodsReceiptLine} and
     *       {@code NewStockWriteOff} describe a write that never happens.
     * </ul>
     *
     * <p><strong>That is why the two directions differ here.</strong> It is not a policy about what
     * clients ought to send, and it is not one construct with two behaviours — it is one question
     * asked of two mechanisms. A future reader who sees only that {@code SalesInvoiceLineView}
     * documents {@code exempt} while {@code NewSalesInvoiceLine} does not, and "simplifies" the two
     * into one, would be documenting a serialisation that does not occur.
     *
     * <p>A record reached from <em>both</em> directions cannot be described both ways, and this
     * method does not try: it takes whichever direction reached it first. That would make the
     * document depend on iteration order, so {@code SerialisedRecordContractIT} refuses any
     * both-directions record that has derived properties at all — and pins the both-directions set,
     * so an empty result cannot read as compliance.
     */
    private Map<String, Object> recordSchema(Class<?> raw, String name,
            Map<String, Type> substitutions, Direction direction, String context) {

        claim(name, raw, context);
        directions.computeIfAbsent(name, key -> java.util.EnumSet.noneOf(Direction.class))
                .add(direction);
        if (!components.containsKey(name)) {
            // Reserved before recursing, so a record referring to itself terminates.
            components.put(name, ordered());

            Map<String, Object> properties = ordered();
            List<String> required = new ArrayList<>();
            Set<String> componentNames = new java.util.LinkedHashSet<>();
            for (RecordComponent component : raw.getRecordComponents()) {
                componentNames.add(component.getName());
                properties.put(component.getName(), schemaFor(component.getGenericType(),
                        substitutions, direction,
                        context + " → " + name + "." + component.getName()));
                if (component.getType().isPrimitive()
                        || component.isAnnotationPresent(Mandatory.class)) {
                    required.add(component.getName());
                }
            }

            if (direction == Direction.RESPONSE) {
                for (Map.Entry<String, Class<?>> derived : jacksonPropertiesOf(raw).entrySet()) {
                    if (componentNames.contains(derived.getKey())) {
                        continue;
                    }
                    properties.put(derived.getKey(), schemaFor(derived.getValue(), substitutions,
                            direction, context + " → " + name + "." + derived.getKey()));
                    // Computed on every write, so it is always present — required in exactly the
                    // sense a primitive component is.
                    required.add(derived.getKey());
                }
            }

            Map<String, Object> schema = ordered();
            schema.put("type", "object");
            // Ordered before `properties`, matching the hand-written Money and UnitCost schemas
            // above, so the document reads the same way whoever wrote the schema.
            if (!required.isEmpty()) {
                schema.put("required", List.copyOf(required));
            }
            schema.put("properties", properties);
            components.put(name, schema);
        }
        return ref(name);
    }

    private Map<String, Object> register(String name,
            java.util.function.Supplier<Map<String, Object>> schema) {
        components.computeIfAbsent(name, key -> schema.get());
        return ref(name);
    }

    /**
     * <strong>Two different types may not share a component schema name.</strong> The second would
     * be silently described by the first — the same failure as a duplicate {@code operationId}, one
     * layer down, and quieter: a duplicate id produces a client that does not compile, while this
     * produces one that compiles and is wrong.
     *
     * <p>⚠️ <strong>Q1 raised this and understated it.</strong> It recorded seven distinct
     * {@code NameRequest} records resolving to one schema, "structurally identical today, so the
     * document is accidentally correct". Measured on 2026-08-03 there were <strong>four</strong>
     * collisions, not one — {@code NameRequest} ×7 across 9 operations, plus
     * {@code ContactDetailsRequest}, {@code VatNumberRequest} and {@code VatStatusRequest} ×2 each:
     * 13 records collapsing into 4 schemas over 15 operations.
     *
     * <p><strong>And "identical today" stopped being true the moment 8a ran.</strong> Two of the
     * seven {@code NameRequest} records guarded {@code name} with {@code Required.text} and five did
     * not, so {@code @Mandatory} lands on two of them — the single merged schema would have declared
     * {@code name} required for nine operations of which five do not require it, or optional for two
     * that do. <strong>8a did not merely coincide with this defect; 8a is what would have created
     * it.</strong> All 13 were renamed apart in the same commit.
     *
     * <p>Scoped to what reaches the spec, deliberately. Service-internal records collide too —
     * {@code CreditNoteServiceImpl.Computation} and {@code SalesInvoiceServiceImpl.Computation}, and
     * the same pair of {@code Rounding} records — and are <strong>known and left alone</strong>:
     * they describe no contract, so failing the build on them would force a rename for nothing,
     * which is how a rule earns the reputation that gets it deleted. If either ever reaches the
     * surface, this fires then, when it means something.
     */
    private void claim(String name, Class<?> raw, String context) {
        Class<?> existing = owners.putIfAbsent(name, raw);
        if (existing != null && !existing.equals(raw)) {
            throw new IllegalStateException(
                    "Two different types would be published as the component schema '" + name
                            + "', so one of them would be described by the other's shape and nothing "
                            + "would say so:\n  " + existing.getName() + "\n  " + raw.getName()
                            + "\nRename one of the records. The convention on this surface is an "
                            + "entity prefix — CustomerNameRequest, SupplierNameRequest — and "
                            + "RoleController.RoleDescriptionRequest is the worked example. Reached "
                            + "via: " + context);
        }
    }

    private static Map<String, Object> ref(String name) {
        Map<String, Object> reference = ordered();
        reference.put("$ref", "#/components/schemas/" + name);
        return reference;
    }

    private static Map<String, Object> type(String jsonType) {
        Map<String, Object> schema = ordered();
        schema.put("type", jsonType);
        return schema;
    }

    private static String simpleNameOf(Type type) {
        if (type instanceof Class<?> raw) {
            return raw.getSimpleName();
        }
        if (type instanceof ParameterizedType parameterized) {
            return simpleNameOf(parameterized.getRawType());
        }
        return type.getTypeName();
    }

    /** Insertion-ordered, so the written document is byte-stable across runs. */
    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }
}
