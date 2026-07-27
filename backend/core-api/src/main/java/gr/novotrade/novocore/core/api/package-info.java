/**
 * NovoCore's published core API — the only NovoCore code that adapters and modules are
 * permitted to depend on.
 *
 * <p>What belongs here: service interfaces, DTOs, value objects and enums. What does not:
 * JPA entities, repositories, service implementations, and any framework type. This module
 * has no compile dependencies at all, and an architecture test asserts that it never gains
 * a dependency on {@code jakarta.persistence} or Spring.
 *
 * <p>The reason for the separate artifact is {@code CLAUDE.md} rule 3. Because an adapter
 * declares a dependency on this module and not on {@code novocore-core}, the core's
 * entities and repositories are physically absent from its compile classpath — so reaching
 * around a service interface is a compile error rather than a code-review question. When an
 * adapter needs something the core does not expose, the fix is to add the method here and
 * implement it in the core.
 *
 * @see <a href="../../../../../../../../../docs/decisions/0003-module-layout-core-api-boundary.md">ADR 0003</a>
 */
package gr.novotrade.novocore.core.api;
