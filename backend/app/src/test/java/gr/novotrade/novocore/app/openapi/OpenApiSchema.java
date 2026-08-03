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
 */
final class OpenApiSchema {

    /** Named component schemas, by schema name. Sorted so the written document is stable. */
    private final Map<String, Map<String, Object>> components = new TreeMap<>();

    /** Which type claimed each schema name, so a second claimant fails rather than being ignored. */
    private final Map<String, Class<?>> owners = new HashMap<>();

    Map<String, Map<String, Object>> components() {
        return components;
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

    Map<String, Object> schemaFor(Type type, String context) {
        return schemaFor(type, Map.of(), context);
    }

    private Map<String, Object> schemaFor(Type type, Map<String, Type> substitutions,
            String context) {

        if (type instanceof TypeVariable<?> variable) {
            Type resolved = substitutions.get(variable.getName());
            if (resolved == null) {
                throw new IllegalStateException("Unresolved type variable " + variable.getName()
                        + " in " + context);
            }
            return schemaFor(resolved, substitutions, context);
        }

        if (type instanceof ParameterizedType parameterized) {
            return parameterizedSchema(parameterized, substitutions, context);
        }

        if (type instanceof Class<?> raw) {
            return classSchema(raw, context);
        }

        throw new IllegalStateException(
                "No OpenAPI schema for " + type + " (" + context + "). Add one to OpenApiSchema "
                        + "rather than letting the spec describe it wrongly.");
    }

    private Map<String, Object> parameterizedSchema(ParameterizedType type,
            Map<String, Type> substitutions, String context) {

        Class<?> raw = (Class<?>) type.getRawType();
        Type[] arguments = type.getActualTypeArguments();

        if (Collection.class.isAssignableFrom(raw)) {
            Map<String, Object> schema = ordered();
            schema.put("type", "array");
            schema.put("items", schemaFor(arguments[0], substitutions, context));
            if (Set.class.isAssignableFrom(raw)) {
                schema.put("uniqueItems", true);
            }
            return schema;
        }
        if (Optional.class.equals(raw)) {
            return schemaFor(arguments[0], substitutions, context);
        }
        if ("org.springframework.http.ResponseEntity".equals(raw.getName())) {
            // A handler that sets headers itself — the attachment download does, for
            // Content-Disposition. The body is the payload; the envelope is transport.
            return schemaFor(arguments[0], substitutions, context);
        }
        if (Map.class.isAssignableFrom(raw)) {
            Map<String, Object> schema = ordered();
            schema.put("type", "object");
            schema.put("additionalProperties", schemaFor(arguments[1], substitutions, context));
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
            return recordSchema(raw, name, resolved, context);
        }

        throw new IllegalStateException(
                "No OpenAPI schema for parameterized " + raw.getName() + " (" + context + ")");
    }

    private Map<String, Object> classSchema(Class<?> raw, String context) {
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
            return recordSchema(raw, raw.getSimpleName(), Map.of(), context);
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
     */
    private Map<String, Object> recordSchema(Class<?> raw, String name,
            Map<String, Type> substitutions, String context) {

        claim(name, raw, context);
        if (!components.containsKey(name)) {
            // Reserved before recursing, so a record referring to itself terminates.
            components.put(name, ordered());

            Map<String, Object> properties = ordered();
            List<String> required = new ArrayList<>();
            for (RecordComponent component : raw.getRecordComponents()) {
                properties.put(component.getName(), schemaFor(component.getGenericType(),
                        substitutions, context + " → " + name + "." + component.getName()));
                if (component.getType().isPrimitive()
                        || component.isAnnotationPresent(Mandatory.class)) {
                    required.add(component.getName());
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
