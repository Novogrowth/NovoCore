package gr.novotrade.novocoretest;

import gr.novotrade.novocore.core.api.security.UserSessions;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boot configuration for the core module's own integration tests, so they need not borrow the
 * deployable application from the {@code app} module.
 *
 * <p><strong>The unusual package is deliberate and load-bearing.</strong> This class ships inside
 * core's test-jar, which the {@code app} module depends on for the shared PostgreSQL harness.
 * {@code NovoCoreApplication} component-scans {@code gr.novotrade.novocore}, so if this class
 * lived anywhere beneath that package it would be scanned into the application's own test
 * context — and because it carries {@code @EnableAutoConfiguration}, autoconfiguration would run
 * a second time and every Spring Data repository bean would be registered twice, failing with
 * {@code BeanDefinitionOverrideException}. That is not hypothetical; it is what happened when
 * this class first lived in {@code gr.novotrade.novocore.core}.
 *
 * <p>{@code gr.novotrade.novocoretest} is a <em>sibling</em> of {@code gr.novotrade.novocore}
 * rather than a child. Component scanning resolves a base package to a directory path, so this
 * tree is genuinely outside the app's scan. Do not move this class under {@code ..novocore..}.
 *
 * <p>{@code @SpringBootConfiguration} rather than {@code @SpringBootApplication}: fewer
 * meta-annotations to leak should this ever end up somewhere scanned after all.
 */
/*
 * All three scans name the core package explicitly rather than relying on this class's own
 * position. Spring Boot anchors component, entity and repository scanning to the package of the
 * configuration class, and this one sits outside the core on purpose (see above) — so without
 * these, entities and repositories are simply not found and every core bean fails to wire.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
/*
 * The web layer is excluded from this context on purpose.
 *
 * A controller in ..core.web.. depends on CurrentUser, which is implemented in the `app` module
 * against the Spring Security context — that is the seam letting the core ask "who is logged in?"
 * without knowing Spring Security exists. The core module's own tests have no `app` on the
 * classpath, so no such bean, and scanning the controllers here fails the whole context.
 *
 * Excluding them is the honest answer rather than a workaround: these tests exercise the core's
 * services against a real database, and the web layer is not part of that scope. The endpoint is
 * tested in the `app` module by ChartOfAccountsEndpointIT, over real HTTP with real
 * authentication, which is the only place the full wiring exists to test.
 *
 * The alternative — a permissive fallback CurrentUser bean in the core for when none is supplied —
 * was rejected. A security component that quietly substitutes a default when its real
 * implementation is missing is exactly the thing that later fails open in production.
 */
@ComponentScan(
        basePackages = CoreTestApplication.CORE_PACKAGE,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = CoreTestApplication.WEB_PACKAGE_PATTERN))
@EntityScan(CoreTestApplication.CORE_PACKAGE)
@EnableJpaRepositories(CoreTestApplication.CORE_PACKAGE)
public class CoreTestApplication {

    static final String CORE_PACKAGE = "gr.novotrade.novocore.core";

    static final String WEB_PACKAGE_PATTERN = "gr\\.novotrade\\.novocore\\.core\\.web\\..*";

    /**
     * A {@link UserSessions} that ends nothing, because in this context there is nothing to end.
     *
     * <p><strong>Not the permissive fallback the class comment above rejects</strong>, and the
     * difference is worth being precise about. That rejection was of a default supplied *inside the
     * core*, which would have applied in production too and would have failed open there. This bean
     * exists only in the core module's test context, where the claim it makes is simply true: there
     * is no servlet container, no login has happened, and no session exists that could be ended.
     *
     * <p>{@code UserServiceImpl} takes {@code UserSessions} as a required constructor argument
     * precisely so that the deployable application cannot start without a real one — an
     * {@code ObjectProvider} with a no-op default would have let a production misconfiguration
     * silently stop evicting anybody. So this has to be declared, and declaring it is the point:
     * somebody removing {@code NovoCoreSessionRegistry} from {@code app} gets a context that refuses
     * to start rather than a system where deactivation quietly stops logging people out.
     *
     * <p>The behaviour this bean cannot exercise is asserted in {@code app} instead, over real HTTP,
     * by {@code SessionEvictionIT}.
     */
    @Bean
    UserSessions noSessionsToEnd() {
        return userId -> 0;
    }
}
