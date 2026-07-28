package gr.novotrade.novocore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces {@code CLAUDE.md} rules 2 and 3 — the ports-and-adapters boundary.
 *
 * <p>The Maven module graph already makes most of this a compile error rather than a test
 * failure (ADR 0003): an adapter depends on {@code novocore-core-api}, so core entities and
 * repositories are not on its classpath. These rules are the second line of defence, and
 * they cover the cases the module graph cannot — chiefly intra-module boundaries inside
 * {@code core} itself, and the day someone adds a convenient dependency to an adapter POM.
 *
 * <p>Several rules below carry {@code allowEmptyShould(true)}. ArchUnit fails a rule whose
 * subject set is empty, which is normally the right default, but the {@code adapters} and
 * {@code modules} packages are deliberately empty until phases 3 and 4. Writing the rules now
 * and letting them sit dormant is the point: the first adapter is born constrained instead of
 * being audited afterwards. Each such rule names the phase that makes it non-vacuous.
 */
class CoreBoundaryRulesTest {

    private static final String CORE_API = "gr.novotrade.novocore.core.api..";
    private static final String CORE_ALL = "gr.novotrade.novocore.core..";
    private static final String CORE_WEB = "gr.novotrade.novocore.core.web..";
    private static final String CORE_EMAIL = "gr.novotrade.novocore.core.email..";
    private static final String ADAPTERS = "gr.novotrade.novocore.adapters..";
    private static final String MODULES = "gr.novotrade.novocore.modules..";

    /** Everything in the core except its published API. */
    private static final DescribedPredicate<JavaClass> CORE_INTERNALS =
            JavaClass.Predicates.resideInAPackage(CORE_ALL)
                    .and(JavaClass.Predicates.resideOutsideOfPackage(CORE_API))
                    .as("core internals (%s excluding %s)", CORE_ALL, CORE_API);

    /**
     * Anything that reads or writes a database. Adapters and modules must go through core
     * service interfaces instead.
     */
    private static final String[] PERSISTENCE_PACKAGES = {
        "jakarta.persistence..",
        "org.hibernate..",
        "org.springframework.data..",
        "org.springframework.jdbc..",
        "org.springframework.transaction..",
        "javax.sql..",
        "java.sql..",
        "org.flywaydb..",
    };

    @Test
    @DisplayName("adapters and modules must not reach into core internals (rule 3)")
    void adaptersAndModulesUseOnlyTheCoreApi() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(ADAPTERS, MODULES)
                .should().dependOnClassesThat(CORE_INTERNALS)
                .because("CLAUDE.md rule 3: adapters and modules call the core only through "
                        + "its defined service interfaces. If something you need is missing, "
                        + "add the method to the core's interface rather than reaching around "
                        + "it.")
                // Non-vacuous from phase 3 (first adapter) and phase 4 (first module).
                .allowEmptyShould(true);

        rule.check(ImportedClasses.production());
    }

    @Test
    @DisplayName("adapters and modules must not touch the database directly (rule 3)")
    void adaptersAndModulesDoNotAccessThePersistenceLayer() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(ADAPTERS, MODULES)
                .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_PACKAGES)
                .because("CLAUDE.md rule 3: never direct database access from an adapter or "
                        + "module. The one legitimate exception is an adapter's own "
                        + "external-ID-to-core-ID mapping table (rule 2); when the first such "
                        + "table is built, narrow this rule to that adapter's own package "
                        + "rather than deleting it.")
                // Non-vacuous from phase 3.
                .allowEmptyShould(true);

        rule.check(ImportedClasses.production());
    }

    @Test
    @DisplayName("core-api stays free of persistence and framework types (ADR 0003)")
    void coreApiHasNoFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(CORE_API)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework..")
                .because("core-api is the artifact adapters compile against. The moment it "
                        + "references a JPA or Spring type, the boundary leaks by another "
                        + "route and the separate module stops buying us anything.");

        rule.check(ImportedClasses.production());
    }

    @Test
    @DisplayName("REST controllers go through core-api, never straight to repositories")
    void webLayerDependsOnlyOnTheCoreApi() {
        DescribedPredicate<JavaClass> coreInternalsOutsideWeb =
                CORE_INTERNALS.and(JavaClass.Predicates.resideOutsideOfPackage(CORE_WEB))
                        .as("core internals outside the web package");

        ArchRule rule = noClasses()
                .that().resideInAPackage(CORE_WEB)
                .should().dependOnClassesThat(coreInternalsOutsideWeb)
                .because("controllers live in the core module for good reason (the web UI is "
                        + "the core's front door, not an adapter), but sharing a module with "
                        + "the implementations means nothing stops a controller injecting a "
                        + "repository except this rule.");
        // allowEmptyShould deliberately NOT set: ChartOfAccountsController and
        // WebExceptionHandler now live in ..core.web.., so this rule has a real subject set and
        // fails if it is ever emptied. Re-adding the allowance would let the package be deleted
        // and take the guarantee with it silently.

        rule.check(ImportedClasses.production());
    }

    /**
     * The mail client, which only the shared email service may hold.
     *
     * <p>{@code jakarta.mail} is the API and {@code org.springframework.mail} the Spring layer
     * over it. Both are named, because either one alone is enough to send an email.
     */
    private static final String[] MAIL_PACKAGES = {
        "jakarta.mail..",
        "org.springframework.mail..",
    };

    @Test
    @DisplayName("only the shared email service may speak SMTP (CLAUDE.md shared core services)")
    void onlyTheEmailServiceTouchesMail() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(CORE_EMAIL)
                .should().dependOnClassesThat().resideInAnyPackage(MAIL_PACKAGES)
                .because("CLAUDE.md: email sending is a shared core service, configured once via "
                        + "Settings and exposed through one interface. Never configure SMTP or "
                        + "send email directly from within a module - that recreates the "
                        + "scattered-credentials problem this rule exists to prevent. Call "
                        + "EmailSender.send instead; if it cannot express what you need, add to "
                        + "that interface rather than around it.");

        rule.check(ImportedClasses.production());
    }

    @Test
    @DisplayName("the app module wires things together and holds no business logic")
    void appModuleDoesNotAccessThePersistenceLayerDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("gr.novotrade.novocore.app..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework.data.repository..")
                .because("app is the Spring Boot bootstrap and cross-cutting configuration. "
                        + "An entity or repository referenced from here is business logic that "
                        + "has escaped the core.")
                // Non-vacuous once app gains configuration classes in step 4.
                .allowEmptyShould(true);

        rule.check(ImportedClasses.production());
    }
}
