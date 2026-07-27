package gr.novotrade.novocore.core.support;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Cross-cutting infrastructure the core needs: JPA auditing and a clock.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class CoreInfrastructureConfiguration {

    /** The name recorded when something happens with no logged-in user behind it. */
    public static final String SYSTEM_ACTOR = "system";

    /**
     * Supplies the username recorded in audit columns and audit log entries.
     *
     * <p>Returns {@link #SYSTEM_ACTOR} unconditionally for now, because authentication does not
     * exist until build step 4. That step replaces this with a lookup against the security
     * context, falling back to {@code system} for genuinely unattended work such as a scheduled
     * backup or a depreciation run.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(SYSTEM_ACTOR);
    }

    /**
     * Injected rather than calling {@code Instant.now()} inline, so that time-dependent
     * behaviour can be tested against a fixed instant instead of by sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
