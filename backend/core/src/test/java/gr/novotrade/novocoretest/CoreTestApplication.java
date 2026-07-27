package gr.novotrade.novocoretest;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
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
@ComponentScan(CoreTestApplication.CORE_PACKAGE)
@EntityScan(CoreTestApplication.CORE_PACKAGE)
@EnableJpaRepositories(CoreTestApplication.CORE_PACKAGE)
public class CoreTestApplication {

    static final String CORE_PACKAGE = "gr.novotrade.novocore.core";
}
