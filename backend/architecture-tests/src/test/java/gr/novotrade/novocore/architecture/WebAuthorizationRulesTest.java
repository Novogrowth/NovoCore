package gr.novotrade.novocore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Four rules about what a controller may do, each closing a hole that would fail silently.
 *
 * <p>All four share a shape: the thing they forbid <em>compiles, runs and looks right</em>. A
 * handler with no permission declaration serves data. A controller reading products unredacted
 * returns a complete-looking response. A controller receiving stock directly creates a lot. A
 * controller throwing {@code IllegalArgumentException} returns a perfectly valid 400. Nothing throws
 * in any of the four, which is exactly why they are build failures rather than review notes — the
 * same argument that made proxy self-invocation an ArchUnit rule after it had bitten three times.
 */
class WebAuthorizationRulesTest {

    private static final String CORE_WEB = "gr.novotrade.novocore.core.web..";
    private static final String REQUIRES = "gr.novotrade.novocore.core.web.Requires";

    /** Everything Spring MVC treats as a route. */
    private static final List<String> MAPPING_ANNOTATIONS = List.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping");

    // -------------------------------------------------------------------------------------------
    // 1. Every route declares a section
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every HTTP handler declares the section it belongs to")
    void everyHandlerDeclaresASection() {
        DescribedPredicate<JavaMethod> mappedToARoute =
                new DescribedPredicate<>("mapped to an HTTP route") {
                    @Override
                    public boolean test(JavaMethod method) {
                        return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
                    }
                };

        ArchCondition<JavaMethod> declareASection =
                new ArchCondition<>("declare @Requires, on the method or its controller") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        boolean declared = method.isAnnotatedWith(REQUIRES)
                                || method.getOwner().isAnnotatedWith(REQUIRES);
                        events.add(new SimpleConditionEvent(method, declared,
                                method.getFullName() + " is mapped to an HTTP route but declares "
                                        + "no @Requires section, so nothing checks a permission "
                                        + "before it runs"));
                    }
                };

        ArchRule rule = methods()
                .that().areDeclaredInClassesThat().resideInAPackage(CORE_WEB)
                .and(mappedToARoute)
                .should(declareASection)
                .because("an endpoint with no declared section is one that nothing checks a "
                        + "permission for. This is the failure mode an annotation introduces that "
                        + "step 4b's inline check did not have — an inline call is visible in the "
                        + "method body, while a missing annotation looks exactly like working "
                        + "code. Two further layers agree with this rule at runtime "
                        + "(EndpointDeclarationCheck refuses to start; SectionAccessInterceptor "
                        + "refuses the request), but this one fails before anybody is affected.");
        // allowEmptyShould deliberately NOT set: there are controllers, so an empty subject set
        // would mean the web package had been emptied and would take the guarantee with it.

        rule.check(ImportedClasses.production());
    }

    // -------------------------------------------------------------------------------------------
    // 2. Controllers read products redacted
    // -------------------------------------------------------------------------------------------

    /**
     * The unredacted reads on {@code ProductService}.
     *
     * <p>Each has a {@code ...For(viewer)} counterpart that applies
     * {@code ProductView.redactedFor}. The plain ones exist for NovoCore's own costing and posting
     * rules, which cannot work from a blanked cost, and are named here rather than derived from the
     * absence of a {@code For} suffix so that adding a method does not silently join the allowed
     * set.
     */
    private static final Set<String> UNREDACTED_PRODUCT_READS = Set.of(
            "all", "active", "find", "require", "findBySku", "requireBySku", "findByEan",
            "bySupplier");

    /**
     * {@code BundleService}'s two reads that also return {@code ProductView}.
     *
     * <p>Covered by the same rule since step 14c. A bundle <em>is</em> a product, so these lists
     * carry the same three restricted fields as any other product list — but the rule below was
     * originally written against {@code ProductService} alone, which left them outside it. The
     * behaviour was never wrong (the controller redacted them by hand); the guarantee was
     * conventional rather than structural, which is the state this project has closed everywhere
     * else it has appeared.
     */
    private static final Set<String> UNREDACTED_BUNDLE_READS = Set.of(
            "allBundles", "bundlesWithUnpricedComponents");

    @Test
    @DisplayName("controllers read products through the redacting variants, never the plain ones")
    void controllersUseTheRedactingProductReads() {
        String because =
                "step 5 recorded this as a named convention and said the first Products "
                        + "controller must be reviewed for it; step 14 is that controller, so the "
                        + "convention becomes a rule. Three fields are at stake — last purchase "
                        + "price, supplier and supplier SKU — and the role they are kept from, "
                        + "Remote/Order Staff, is the one most likely to be holding a scanner in "
                        + "front of these endpoints. Call the ...For(viewer) variant instead; "
                        + "every plain read has one.";

        ArchRule products = noClasses()
                .that().resideInAPackage(CORE_WEB)
                .should(callAnyOf(
                        "gr.novotrade.novocore.core.api.product.ProductService",
                        UNREDACTED_PRODUCT_READS))
                .because(because);

        ArchRule bundles = noClasses()
                .that().resideInAPackage(CORE_WEB)
                .should(callAnyOf(
                        "gr.novotrade.novocore.core.api.bundle.BundleService",
                        UNREDACTED_BUNDLE_READS))
                .because(because + " A bundle is a product, so its lists carry the same fields — "
                        + "allBundlesFor and bundlesWithUnpricedComponentsFor exist for this.");

        products.check(ImportedClasses.production());
        bundles.check(ImportedClasses.production());
    }

    // -------------------------------------------------------------------------------------------
    // 3. Stock moves through documents, never directly
    // -------------------------------------------------------------------------------------------

    /**
     * The lower layer of {@code InventoryService}: methods that move stock without a document.
     *
     * <p>Each is called by a document service — Goods Receipt, Sales Invoice, Freight Allocation —
     * which posts the journal entry that goes with the movement, in the same transaction. Reached
     * directly from HTTP they would move stock with <strong>no document and no posting</strong>,
     * leaving the Inventory control account disagreeing with what the lots carry: the precise
     * invariant ADR 0015 restored and {@code WholeScenarioIT} sweeps for.
     *
     * <p>{@code writeOff} and {@code reverseWriteOff} are deliberately <em>absent</em> from this
     * list: a stock write-off is itself a document, it posts, and it is exposed on purpose.
     */
    private static final Set<String> LOWER_LAYER_INVENTORY_WRITES = Set.of(
            "receive", "unreceive", "consume", "reverseConsumption",
            "applyLandedCost", "removeLandedCost");

    @Test
    @DisplayName("stock moves through documents — no controller calls the lower inventory layer")
    void stockMovesThroughDocumentsOnly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(CORE_WEB)
                .should(callAnyOf(
                        "gr.novotrade.novocore.core.api.inventory.InventoryService",
                        LOWER_LAYER_INVENTORY_WRITES))
                .because("these move stock without a document behind them. A Goods Receipt, a "
                        + "Sales Invoice or a Freight Allocation calls them and posts the journal "
                        + "entry in the same transaction; an HTTP route to them would create or "
                        + "consume a lot with nothing posted, so the Inventory control account "
                        + "would stop agreeing with what the lots carry. Record the document "
                        + "instead — that is the whole point of the two-layer design.");

        rule.check(ImportedClasses.production());
    }

    // -------------------------------------------------------------------------------------------
    // 4. A client's mistake is never signalled with the exception that means ours
    // -------------------------------------------------------------------------------------------

    /**
     * <strong>The web layer may not construct {@link IllegalArgumentException}.</strong>
     *
     * <p>Step 14 settled that an {@code IllegalArgumentException} reaching {@code WebExceptionHandler}
     * is a mistake in <em>calling code</em>, so its message is logged and the caller gets a bare
     * {@code 400 "Bad request."} — correct, because such a message describes internal state. The web
     * layer then used that same exception to tell callers what they had got wrong, and the handler did
     * exactly what it was designed to do with it.
     *
     * <p><strong>This has now happened twice in one step, which is what earns it a rule.</strong>
     * Defect 5: seventeen parameter-guidance messages across nine controllers discarded. Defect 9,
     * found by writing this rule and running it: {@code GET /api/email/outbox/{id}} answered an unknown
     * id with {@code 400 "Bad request."} where every other route on the surface answers
     * {@code 404 "Not found."} — so on that one route a client could not tell a malformed request from
     * a missing record. Same pattern as proxy self-invocation: it bit repeatedly, each time looking
     * like working code, and the remedy each time was the same one sentence.
     *
     * <p>The remedy: {@link gr.novotrade.novocore.core.web.InvalidRequestException} when the
     * <em>request</em> is wrong and the caller should be told how,
     * {@code gr.novotrade.novocore.core.web.Required} for a missing body field, and the core's own
     * {@code ...NotFoundException} when an id names nothing.
     *
     * <p><strong>Narrow on purpose, and here is what it cannot see.</strong> It forbids
     * <em>constructing</em> the exception in this package, so catching one (as
     * {@code NovoCoreJsonModule} legitimately does) and {@code IllegalStateException} for a genuinely
     * unreachable branch are both untouched. It says nothing about the other half of the same
     * anti-pattern — a wire value null-checked with {@code Objects.requireNonNull}, which is defect 7
     * — because {@code ListResponse} uses that idiom correctly on our own arguments and ArchUnit
     * cannot tell a caller's omission from a programmer's. That half is guarded behaviourally by
     * {@code PermissionSweepIT.noRouteFailsOnAnEmptyBody}, which asks every state-changing route for a
     * body it does not have, and named in {@code CLAUDE.md} for the cases neither can reach.
     */
    @Test
    @DisplayName("no controller signals a client's mistake with IllegalArgumentException")
    void clientMistakesAreNotProgrammingErrors() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(CORE_WEB)
                .should(constructAnInstanceOf(IllegalArgumentException.class))
                .because("WebExceptionHandler treats IllegalArgumentException as a mistake in our "
                        + "own code: it logs the message and answers a bare 400 \"Bad request.\" So "
                        + "an IllegalArgumentException raised here to tell a caller what they got "
                        + "wrong produces a valid-looking response with the explanation removed — "
                        + "which is exactly what happened to seventeen controller messages in step "
                        + "15's defect 5, and again to the outbox's unknown-id 404 in defect 9. Use "
                        + "InvalidRequestException (the request is wrong, say how), Required.field "
                        + "(a body field is missing), or the core's own ...NotFoundException (the id "
                        + "names nothing).");

        rule.check(ImportedClasses.production());
    }

    /**
     * Matches construction of one exception type.
     *
     * <p>Construction rather than any reference, so that {@code catch (IllegalArgumentException ...)}
     * — which {@code NovoCoreJsonModule} does correctly, translating the JDK's own refusal of an
     * unknown currency into a message about the request — is not caught by a rule aimed at throwing.
     */
    private static ArchCondition<JavaClass> constructAnInstanceOf(Class<?> exceptionType) {
        return new ArchCondition<>("construct " + exceptionType.getSimpleName()) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getConstructorCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().isAssignableTo(exceptionType))
                        .forEach(call -> events.add(SimpleConditionEvent.satisfied(call,
                                call.getDescription() + " — a caller's mistake must not be raised "
                                        + "as " + exceptionType.getSimpleName())));
            }
        };
    }

    // -------------------------------------------------------------------------------------------

    /**
     * Matches a call to any of {@code methodNames} on {@code ownerFullName}.
     *
     * <p>Written out rather than composed from ArchUnit's call predicates because the composed form
     * reads as punctuation, and a rule nobody can read is a rule someone deletes rather than fixes.
     */
    private static ArchCondition<JavaClass> callAnyOf(String ownerFullName, Set<String> methodNames) {
        return new ArchCondition<>("call " + shortName(ownerFullName) + "." + sorted(methodNames)) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    boolean forbidden =
                            call.getTargetOwner().getFullName().equals(ownerFullName)
                                    && methodNames.contains(call.getTarget().getName());
                    if (forbidden) {
                        events.add(SimpleConditionEvent.satisfied(call,
                                call.getDescription() + " — "
                                        + shortName(ownerFullName) + "."
                                        + call.getTarget().getName() + " is not callable from the "
                                        + "web layer"));
                    }
                }
            }
        };
    }

    private static String shortName(String fullName) {
        return fullName.substring(fullName.lastIndexOf('.') + 1);
    }

    private static String sorted(Set<String> names) {
        return names.stream().sorted().toList().toString();
    }
}
