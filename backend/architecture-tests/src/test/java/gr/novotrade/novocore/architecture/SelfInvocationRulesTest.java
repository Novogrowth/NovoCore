package gr.novotrade.novocore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The self-invocation trap: a class calling its own {@code @Transactional} method.
 *
 * <h2>Why this rule exists</h2>
 *
 * <p>Spring applies {@code @Transactional} with a proxy. A call from one method of an object to
 * another method <em>of the same object</em> goes straight to the object and never through that
 * proxy, so the annotation does nothing whatsoever. Nothing fails, nothing warns, and the code
 * reads exactly like code that works.
 *
 * <p>This codebase has been bitten by it twice. Step 11 discovered it while designing
 * {@code EmailOutbox} and recorded the reasoning in that class; step 12 then wrote
 * {@code RestoreVerifier} the obvious way and reintroduced it — the whole restore check would have
 * run with no transaction management at all, and the symptom would have been nothing until a
 * failure needed recording and was not. Being caught twice by reading is not a strategy, so it is
 * a build failure now.
 *
 * <h2>What it forbids, and why the line is drawn exactly there</h2>
 *
 * <p>Two rules, because the two shapes of self-invocation are not equally wrong and a rule that
 * treated them alike would be useless here. The first draft of this class forbade both and
 * reported <strong>44 violations</strong>, essentially all of them harmless — and a rule that cries
 * wolf 44 times is one somebody deletes.
 *
 * <ol>
 *   <li><strong>A non-transactional method must not call its own class's {@code @Transactional}
 *       method.</strong> The proxy is bypassed and there is no transaction at all. This is the
 *       shape that bit us twice, and narrowing to it turned 44 findings into a handful — of which
 *       two were real defects, including one in the audit log.
 *   <li><strong>Nothing may self-invoke a method whose propagation is not the default.</strong> A
 *       {@code REQUIRES_NEW} reached through a self-call silently joins the caller's transaction
 *       instead of starting its own — so an audit entry written to survive a rollback is rolled
 *       back with it. This one is wrong even when the caller <em>is</em> transactional, which is
 *       precisely the case rule 1 has to allow.
 * </ol>
 *
 * <p>What is deliberately allowed: a {@code @Transactional} method calling another on the same
 * class with default propagation. The inner call joins the outer transaction, which is what the
 * code means and what would happen through the proxy anyway. Forbidding it would outlaw
 * {@code SettingsServiceImpl.requireInt} calling its own {@code require}, which is ordinary.
 *
 * <p>The remedy for a genuine violation is always the same and is the one both steps arrived at:
 * move the transactional methods into their own bean. {@code EmailOutbox} / {@code EmailDispatcher}
 * and {@code RestoreCheckJournal} / {@code RestoreVerifier} are the worked examples in the tree.
 *
 * <p>This cannot cover every proxy-based annotation ({@code @Async}, {@code @Cacheable},
 * {@code @PreAuthorize} fail identically), and it cannot see a call made through a lambda captured
 * elsewhere. {@code CLAUDE.md} names the general anti-pattern for that reason; these two rules
 * catch the cases that have actually happened.
 */
class SelfInvocationRulesTest {

    private static final String TRANSACTIONAL =
            "org.springframework.transaction.annotation.Transactional";

    @Test
    @DisplayName("a non-transactional method never calls its own class's @Transactional method")
    void noSelfInvocationFromOutsideATransaction() {
        rule().check(ImportedClasses.production());
    }

    @Test
    @DisplayName("nothing self-invokes a method whose propagation is not the default")
    void noSelfInvocationOfNonDefaultPropagation() {
        propagationRule().check(ImportedClasses.production());
    }

    /**
     * Proves the rule can fail.
     *
     * <p>Written because a rule nobody has seen reject anything is indistinguishable from a rule
     * that matches nothing — which is exactly how the {@code ..core.web..} boundary rule was found
     * to be passing vacuously in step 4b. The probe below is the {@code RestoreVerifier} bug in
     * miniature.
     */
    @Test
    @DisplayName("the rule actually fails against the bug it exists to catch")
    void ruleFailsAgainstAProbe() {
        JavaClasses probe = new ClassFileImporter().importClasses(SelfInvokingProbe.class);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> rule().check(probe))
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("SelfInvokingProbe")
                        .contains("recordOutcome"));
    }

    @Test
    @DisplayName("the propagation rule fails against a self-invoked REQUIRES_NEW")
    void propagationRuleFailsAgainstAProbe() {
        // The AuditLogServiceImpl bug in miniature, and the reason this second rule exists: the
        // caller here IS transactional, so rule 1 deliberately permits it and only this one sees
        // that the REQUIRES_NEW is being thrown away.
        JavaClasses probe = new ClassFileImporter().importClasses(RequiresNewProbe.class);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> propagationRule().check(probe))
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("RequiresNewProbe")
                        .contains("auditIt"));

        // And rule 1 must NOT fire on it, or the two rules would be reporting the same thing and
        // the distinction they exist to draw would be imaginary.
        rule().check(probe);
    }

    @Test
    @DisplayName("a call to another bean's @Transactional method is fine")
    void callingAnotherBeanIsAllowed() {
        // The remedy the rule pushes people towards must not itself trip it, or the rule would be
        // unsatisfiable and would simply be suppressed.
        JavaClasses clean = new ClassFileImporter().importClasses(
                CleanCaller.class, CleanJournal.class);

        rule().check(clean);
    }

    private static ArchRule rule() {
        return noClasses()
                .should(selfInvokeTransactionalMethodsFromOutsideATransaction())
                .because("Spring applies @Transactional with a proxy, and a call from one method "
                        + "of an object to another method of the same object never goes through "
                        + "it — so the annotation silently does nothing and the work runs with no "
                        + "transaction at all. This has bitten this codebase twice (EmailOutbox in "
                        + "step 11, RestoreVerifier in step 12). Move the transactional methods "
                        + "into their own bean, as EmailOutbox/EmailDispatcher and "
                        + "RestoreCheckJournal/RestoreVerifier do.");
    }

    private static ArchRule propagationRule() {
        return noClasses()
                .should(selfInvokeNonDefaultPropagation())
                .because("a self-call cannot start a new transaction, so REQUIRES_NEW reached "
                        + "that way silently joins the caller's instead. An audit entry annotated "
                        + "to survive a rollback is then rolled back with the thing it was "
                        + "recording — which is the exact failure the annotation exists to "
                        + "prevent, wearing the appearance of being handled.");
    }

    private static ArchCondition<JavaClass> selfInvokeTransactionalMethodsFromOutsideATransaction() {
        return new ArchCondition<>(
                "call their own @Transactional methods from a method that is not itself transactional") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaCodeUnit caller : javaClass.getCodeUnits()) {
                    // A transactional caller is allowed: the inner call joins the outer
                    // transaction, which is both what the code means and what the proxy would
                    // have done. Only an entry from outside any transaction loses the guarantee.
                    if (caller.isAnnotatedWith(TRANSACTIONAL)) {
                        continue;
                    }
                    for (JavaMethodCall call : caller.getMethodCallsFromSelf()) {
                        transactionalSelfTarget(javaClass, call).ifPresent(target ->
                                events.add(SimpleConditionEvent.satisfied(javaClass,
                                        "%s is not @Transactional and calls its own @Transactional "
                                                .formatted(caller.getFullName())
                                                + "method %s at %s — the proxy is bypassed, so the "
                                                        .formatted(target.getName(),
                                                                call.getSourceCodeLocation())
                                                + "annotation does nothing. Move it to its own "
                                                + "bean.")));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> selfInvokeNonDefaultPropagation() {
        return new ArchCondition<>("self-invoke a method with non-default propagation") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaCodeUnit caller : javaClass.getCodeUnits()) {
                    for (JavaMethodCall call : caller.getMethodCallsFromSelf()) {
                        transactionalSelfTarget(javaClass, call)
                                .filter(SelfInvocationRulesTest::declaresNonDefaultPropagation)
                                .ifPresent(target -> events.add(SimpleConditionEvent.satisfied(
                                        javaClass,
                                        "%s self-invokes %s at %s, which declares a non-default "
                                                .formatted(caller.getFullName(), target.getName(),
                                                        call.getSourceCodeLocation())
                                                + "propagation — silently ignored, because a "
                                                + "self-call cannot start a new transaction.")));
                    }
                }
            }
        };
    }

    /** The call's target, when it is a {@code @Transactional} method on the calling class itself. */
    private static Optional<JavaMethod> transactionalSelfTarget(JavaClass javaClass,
            JavaMethodCall call) {
        if (!isSelfCall(javaClass, call)) {
            return Optional.empty();
        }
        return resolve(call).filter(target -> target.isAnnotatedWith(TRANSACTIONAL));
    }

    /**
     * Whether the method asks for anything other than {@code Propagation.REQUIRED}.
     *
     * <p>Read off the annotation reflectively rather than by importing {@code Propagation}, so this
     * keeps working if the annotation gains attributes and does not need the enum on the rule
     * module's compile path.
     */
    private static boolean declaresNonDefaultPropagation(JavaMethod method) {
        return method.tryGetAnnotationOfType(TRANSACTIONAL)
                .map(annotation -> annotation.get("propagation")
                        .map(Object::toString)
                        .filter(value -> !value.endsWith("REQUIRED"))
                        .isPresent())
                .orElse(false);
    }

    /**
     * Whether a call targets the very class it is made from.
     *
     * <p>Compared by name rather than by identity, and restricted to the exact owner: a call to an
     * inherited method on a superclass is a different question, and one this rule deliberately
     * does not try to answer — the proxy still wraps the bean, and the shapes that go wrong there
     * are not the ones that have happened here.
     */
    private static boolean isSelfCall(JavaClass javaClass, JavaMethodCall call) {
        return call.getTargetOwner().getFullName().equals(javaClass.getFullName());
    }

    private static Optional<JavaMethod> resolve(JavaMethodCall call) {
        // Resolves against the imported graph. The target is in the same class as the caller, so
        // it is always present here; the guard is for a future widening of the rule.
        return call.getTarget().resolveMember();
    }

    // -------------------------------------------------------------------------------------
    // Fixtures. Not Spring beans and never wired anywhere — they exist to be inspected as
    // bytecode, which is the only way to prove an ArchUnit rule can both fail and pass.
    // -------------------------------------------------------------------------------------

    /** The RestoreVerifier bug in miniature: a plain method calling its own transactional one. */
    @SuppressWarnings("unused")
    static class SelfInvokingProbe {

        void doTheWork() {
            recordOutcome("done");
        }

        @Transactional
        void recordOutcome(String outcome) {
            List.of(outcome).forEach(String::strip);
        }
    }

    /**
     * A transactional method self-invoking a {@code REQUIRES_NEW} one.
     *
     * <p>Rule 1 permits this shape on purpose — the caller is inside a transaction, so nothing is
     * running unmanaged. Only rule 2 can see that the new transaction the inner method asked for
     * never happens, which is what makes an audit entry roll back with the work it was recording.
     */
    @SuppressWarnings("unused")
    static class RequiresNewProbe {

        @Transactional
        void doTheWork() {
            auditIt("done");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        void auditIt(String outcome) {
            List.of(outcome).forEach(String::strip);
        }
    }

    /** The remedy: the transactional method lives on a collaborator. */
    @SuppressWarnings("unused")
    static class CleanCaller {

        private final CleanJournal journal = new CleanJournal();

        void doTheWork() {
            journal.recordOutcome("done");
        }
    }

    @SuppressWarnings("unused")
    static class CleanJournal {

        @Transactional
        void recordOutcome(String outcome) {
            List.of(outcome).forEach(String::strip);
        }
    }
}
