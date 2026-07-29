package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.web.json.NovoCoreJsonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the two things every endpoint depends on: the permission interceptor and the money format.
 *
 * <p>Both are deliberately global rather than opt-in per controller. A serialiser applied per field
 * would be forgotten on the field that mattered, and a permission check applied per controller is
 * step 4b's inline call multiplied by twenty.
 */
@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

    private final CurrentUser currentUser;

    WebConfiguration(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Amounts and quantities as strings, never JSON numbers.
     *
     * <p>Registered as a bean so Boot applies it to the one shared {@code ObjectMapper}, which is
     * what makes it impossible for a controller to serialise {@code Money} some other way. See
     * {@link NovoCoreJsonModule} for why the format is what it is.
     */
    @Bean
    NovoCoreJsonModule novoCoreJsonModule() {
        return new NovoCoreJsonModule();
    }

    /**
     * Applies {@link Requires} to every {@code /api/**} handler.
     *
     * <p>Scoped to {@code /api/**} because that is what {@link gr.novotrade.novocore.core.api
     * .security.Section} governs. Spring Security's {@code /login} and {@code /logout} are the
     * framework's, not ours, and the actuator endpoints are gated by the filter chain and by Caddy.
     * Widening this pattern without widening {@code Section} would refuse those routes outright,
     * since an undeclared handler is refused.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SectionAccessInterceptor(currentUser))
                .addPathPatterns("/api/**");
    }
}
