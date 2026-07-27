/**
 * NovoCore's core implementation: JPA entities, repositories, service implementations, the
 * journal posting engine, and the REST controllers that serve the frontend.
 *
 * <p>Organised by domain slice ({@code account}, {@code tax}, {@code charge}, {@code product},
 * {@code customer}, {@code supplier}, {@code asset}, {@code inventory}, {@code ledger},
 * {@code security}), not by technical layer, so that everything belonging to one concept sits
 * together.
 *
 * <p>Entities are package-private within their slice. That is load-bearing rather than tidiness:
 * because {@code ..core.tax.VatClass} and {@code ..core.account.Account} are not visible outside
 * their packages, {@code ..core.charge.ChargeType} physically cannot hold JPA associations to
 * them and holds validated ids instead. So ADR 0003's boundary holds between slices of the core,
 * not only between the core and its adapters, without needing a further architecture rule.
 *
 * <p>Only {@code novocore-app} may depend on this module. Adapters and modules depend on
 * {@code novocore-core-api} instead.
 *
 * <p>REST controllers live here, in {@code ..core.web..}, rather than in {@code app},
 * because the web UI is the core's own front door — not an "adapter" in the
 * ports-and-adapters sense, which here means an external-system integration. Since those
 * controllers sit in the same module as the implementations, an architecture test forbids
 * {@code ..core.web..} from depending on anything but {@code ..core.api..}, so a controller
 * cannot bypass a service interface and reach a repository directly.
 */
package gr.novotrade.novocore.core;
