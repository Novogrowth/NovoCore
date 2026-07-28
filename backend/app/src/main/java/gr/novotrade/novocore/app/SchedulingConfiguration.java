package gr.novotrade.novocore.app;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler, which the email dispatcher's {@code @Scheduled} method needs.
 *
 * <p><strong>Here rather than in the core, deliberately.</strong> Whether background work runs is
 * a property of the running application, not of the core's business logic, and putting it here
 * has a concrete benefit: the core's own integration tests build a context containing a fully
 * wired dispatcher that never fires by itself. They drive it by calling it, so retry counts and
 * backoff are asserted rather than waited for, and no test is flaky because a poller ran at an
 * unhelpful moment.
 *
 * <p>The scheduler is single-threaded by default, which is what the outbox dispatcher wants:
 * cycles cannot overlap. The first thing to arrive here that genuinely needs concurrency — the
 * step 12 backup run alongside a mail backlog — is the point at which a pool size belongs in
 * {@code application.yml}, not before.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {
}
