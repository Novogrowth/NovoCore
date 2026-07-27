package gr.novotrade.novocore.core.support;

import gr.novotrade.novocore.core.api.security.CurrentUser;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cross-cutting infrastructure the core needs: JPA auditing, a clock, and password hashing.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class CoreInfrastructureConfiguration {

    /** The name recorded when something happens with no logged-in user behind it. */
    public static final String SYSTEM_ACTOR = "system";

    /**
     * Supplies the username recorded in audit columns and audit log entries.
     *
     * <p>Reads the authenticated user when there is one and falls back to {@link #SYSTEM_ACTOR}
     * otherwise. Both cases are normal: a scheduled backup, a depreciation run and the Flyway
     * seed are genuinely actorless, so the fallback records that honestly instead of attributing
     * unattended work to whoever happened to log in last.
     *
     * <p>{@link CurrentUser} is resolved through an {@link ObjectProvider} because it is
     * implemented in the {@code app} module, against the security context. The core module's own
     * integration tests run without a web layer and therefore without that bean, and they must
     * still be able to write audited records — so its absence falls back rather than failing.
     */
    @Bean
    public AuditorAware<String> auditorAware(ObjectProvider<CurrentUser> currentUser) {
        return () -> {
            CurrentUser resolved = currentUser.getIfAvailable();
            return Optional.of(resolved == null
                    ? SYSTEM_ACTOR
                    : resolved.username().orElse(SYSTEM_ACTOR));
        };
    }

    /**
     * Hashes and verifies passwords. Lives in the core because the core verifies passwords itself
     * rather than handing hashes out — see {@code UserService.authenticate}.
     *
     * <p>A delegating encoder, so every stored hash carries its algorithm as a prefix
     * ({@code {bcrypt}$2a$...}). That is what makes a future migration to a stronger algorithm
     * possible without invalidating every existing password: the encoder reads the prefix to
     * choose how to verify, and new hashes are written with the current default. A bare
     * {@code BCryptPasswordEncoder} stores no such marker and would leave no upgrade path.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
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
