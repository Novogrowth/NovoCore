package gr.novotrade.novocore.app;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers {@link RouteCoverage} into the MVC chain, for tests that want to know what they drove.
 *
 * <p>A {@code @TestConfiguration} imported explicitly, never a scanned component: this exists only
 * to observe a test run, and an interceptor that ships in the application would be an observer
 * nobody asked for on every production request.
 *
 * <p><strong>{@code LOWEST_PRECEDENCE} is the load-bearing line in this file.</strong> Interceptors
 * run their {@code preHandle} in registration order, and {@code WebConfiguration} registers
 * {@code SectionAccessInterceptor} at the default order of 0. Placing this one last means it is
 * reached only when the permission check has already allowed the request through — so a route
 * refused with 403 is not recorded as covered. Step 15 deliberately sweeps all 133 routes as a
 * restricted role expecting refusals, and without this ordering that sweep alone would mark the
 * entire surface covered.
 */
@TestConfiguration(proxyBeanMethods = false)
class RouteCoverageConfiguration {

    @Bean
    RouteCoverage routeCoverage(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        return new RouteCoverage(handlerMappings);
    }

    @Bean
    WebMvcConfigurer routeCoverageRegistrar(RouteCoverage routeCoverage) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(routeCoverage)
                        .addPathPatterns("/api/**")
                        .order(Ordered.LOWEST_PRECEDENCE);
            }
        };
    }
}
