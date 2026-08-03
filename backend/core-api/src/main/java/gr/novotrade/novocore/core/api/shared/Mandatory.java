package gr.novotrade.novocore.core.api.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <strong>This component is never absent.</strong> On a request it is a field the caller must supply;
 * on a response it is a field we always set. Declared here because <em>reflection cannot see it</em>.
 *
 * <h2>The hole this fills</h2>
 *
 * <p>{@code OpenApiSchema} marks a record component {@code required} when it is a Java
 * <em>primitive</em>, because a primitive cannot be null and Jackson's
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} refuses an absent one before any handler runs. That rule is
 * true in both directions and is the whole of what reflection can establish.
 *
 * <p>A <em>reference-typed</em> component required by a compact constructor — {@link Required#field},
 * {@link Required#text} or {@code Objects.requireNonNull} — is mandatory in exactly the same sense and
 * completely invisible to reflection, because reflection cannot look inside a constructor body. So the
 * spec described 339 always-present components as optional, and the generated TypeScript made every
 * one of them {@code T | undefined}.
 *
 * <p>This annotation is the declaration that closes it. {@code OpenApiSchema} reads it exactly as it
 * reads {@code isPrimitive()}, and the two together are the {@code required} list.
 *
 * <h2>⚠️ It is not free-hand — it is cross-checked against the constructor</h2>
 *
 * <p>{@code MandatoryDeclarationRulesTest} reads the canonical constructor's <em>bytecode</em> and
 * fails the build in <strong>both</strong> directions: a guarded component without this annotation is
 * a contract that says "optional" about something the server refuses, and an annotated component the
 * constructor does not guard is a promise nothing enforces. Without that check this would be several
 * hundred hand-applied assertions that nothing verifies — <em>a fact established by reading, then
 * built upon</em>, at scale.
 *
 * <h2>What it means on a response, and why that is the larger half</h2>
 *
 * <p>On a response record the guard asserts <em>our own</em> invariant, and it holds structurally
 * rather than by convention: a guarded component cannot be null in a constructed instance, because
 * the constructor refuses it — so a redaction that nulled one would throw rather than emit a
 * shortened body. {@code application.yml}'s {@code default-property-inclusion: non_null} is the only
 * mechanism that omits a property, and nothing in this codebase declares {@code @JsonInclude},
 * {@code @JsonIgnore} or a custom serialiser to override it. Hence: non-null, therefore always on the
 * wire, therefore {@code required}.
 *
 * <p>That is what lets {@code tsc} enforce test-fixture completeness — the one thing no test in the
 * frontend can do honestly, because every other candidate source of truth about the wire is
 * hand-authored.
 *
 * <h2>When NOT to use this</h2>
 *
 * <p>When the requirement is <em>conditional</em> — see {@link ConditionallyMandatory}. A field
 * required only in some shape of the record is not {@code required} in OpenAPI's sense, and declaring
 * it so publishes a contract that contradicts itself.
 *
 * <h2>⚠️ The set of annotated components is a lower bound on what is mandatory</h2>
 *
 * <p>The cross-check can only see the three guard forms above. A component made mandatory by an
 * inline {@code if (x == null) throw} is invisible to it — {@code EmailMessage.subject} and
 * {@code EmailMessage.body} are the known cases, and are not on the HTTP surface. So the rule's
 * second direction must never be read as <em>"everything mandatory is annotated"</em>. An incomplete
 * {@code required} list is still true; a wrong one is worse than none.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Mandatory {
}
