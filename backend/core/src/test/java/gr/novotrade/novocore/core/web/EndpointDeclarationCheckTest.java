package gr.novotrade.novocore.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The startup check, proven to refuse — which is the only thing that makes it worth having.
 *
 * <p>{@code EndpointDeclarationCheck} is one of three layers enforcing that every {@code /api/**}
 * handler declares a section, and it is the layer that is hardest to observe: it fires during
 * context refresh, and when it is working correctly nothing happens. A check that silently passes is
 * indistinguishable from a check that is broken, so this drives it directly with hand-registered
 * handlers rather than trusting that it would have fired.
 *
 * <p>No Spring context and no database: the mapping is built by hand, which is also why this can
 * assert the failure cases without a deliberately broken application to start.
 */
class EndpointDeclarationCheckTest {

    @Test
    @DisplayName("an /api/** handler with no @Requires refuses to let the application start")
    void undeclaredHandlerFailsStartup() {
        RequestMappingHandlerMapping mapping = mappingWith("/api/probe", new Undeclared(), "handle");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> check(mapping).onApplicationEvent(refreshed()))
                .withMessageContaining("Refusing to start")
                .withMessageContaining("no @Requires declaration")
                .withMessageContaining("Undeclared.handle");
    }

    @Test
    @DisplayName("a declaration on the controller class is enough for its handlers")
    void classLevelDeclarationSatisfiesTheCheck() {
        RequestMappingHandlerMapping mapping =
                mappingWith("/api/declared", new DeclaredOnClass(), "handle");

        assertThatCode(() -> check(mapping).onApplicationEvent(refreshed()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@Requires(level = NONE) is refused — every role satisfies it")
    void accessLevelNoneIsRefused() {
        RequestMappingHandlerMapping mapping =
                mappingWith("/api/none", new DeclaredWithNone(), "handle");

        // NONE as a *requirement* is satisfied by every role, so an endpoint declaring it would be
        // readable by anyone authenticated. That is precisely what a declaration exists to prevent,
        // which makes it worse than no declaration at all: it looks deliberate.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> check(mapping).onApplicationEvent(refreshed()))
                .withMessageContaining("meaningless");
    }

    @Test
    @DisplayName("a handler outside /api/** is not governed by sections and is left alone")
    void unGovernedPathsAreIgnored() {
        RequestMappingHandlerMapping mapping = mappingWith("/login", new Undeclared(), "handle");

        // Spring Security's own endpoints are not ours and have no section. Refusing them would
        // mean the application could not start with a login page.
        assertThatCode(() -> check(mapping).onApplicationEvent(refreshed()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a reserved section is refused — nothing is built behind it yet")
    void reservedSectionIsRefused() {
        RequestMappingHandlerMapping mapping =
                mappingWith("/api/reserved", new DeclaredWithReservedSection(), "handle");

        assertThat(Section.SALES_ORDER_FULFILLMENT.isAvailable()).isFalse();
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> check(mapping).onApplicationEvent(refreshed()))
                .withMessageContaining("is reserved");
    }

    @Test
    @DisplayName("a second handler mapping is checked too — a real context has two of them")
    void everyHandlerMappingIsChecked() {
        // The defect this was found by: a real application has both Spring MVC's
        // requestMappingHandlerMapping and the actuator's controllerEndpointHandlerMapping, and an
        // earlier version asked the provider for "the" mapping and died with
        // NoUniqueBeanDefinitionException at startup. Checking only the first would have been the
        // quieter and worse fix — it would leave routes on the other mapping unexamined while
        // reporting success.
        RequestMappingHandlerMapping clean =
                mappingWith("/api/declared", new DeclaredOnClass(), "handle");
        RequestMappingHandlerMapping second =
                mappingWith("/api/second", new Undeclared(), "handle");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new EndpointDeclarationCheck(providerOf(clean, second))
                        .onApplicationEvent(refreshed()))
                .withMessageContaining("/api/second");
    }

    @Test
    @DisplayName("with no handler mapping at all — a non-web context — the check does nothing")
    void nonWebContextIsANoOp() {
        assertThatCode(() -> new EndpointDeclarationCheck(emptyProvider())
                .onApplicationEvent(refreshed()))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------------------------

    private static EndpointDeclarationCheck check(RequestMappingHandlerMapping mapping) {
        return new EndpointDeclarationCheck(providerOf(mapping));
    }

    private static RequestMappingHandlerMapping mappingWith(
            String path, Object handler, String methodName) {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        Method method = Stream.of(handler.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        mapping.registerMapping(
                RequestMappingInfo.paths(path).methods(RequestMethod.GET).build(), handler, method);
        return mapping;
    }

    private static ContextRefreshedEvent refreshed() {
        // The check never reads the event, only the mapping, so a stand-in context is enough.
        return new ContextRefreshedEvent(
                new org.springframework.context.support.StaticApplicationContext());
    }

    /**
     * A provider over however many mappings are given.
     *
     * <p>{@code stream()} is the method that matters, and that is not incidental: a real
     * application has <strong>two</strong> {@code RequestMappingHandlerMapping} beans — Spring
     * MVC's and the actuator's — so an earlier version of the check that asked for "the" mapping
     * failed at startup with {@code NoUniqueBeanDefinitionException}. These stubs are shaped like
     * the real thing so a test cannot pass against a check that would not survive a real context.
     */
    private static ObjectProvider<RequestMappingHandlerMapping> providerOf(
            RequestMappingHandlerMapping... mappings) {
        return new ObjectProvider<>() {
            @Override
            public java.util.stream.Stream<RequestMappingHandlerMapping> stream() {
                return Stream.of(mappings);
            }

            @Override
            public RequestMappingHandlerMapping getObject() {
                return mappings[0];
            }

            @Override
            public RequestMappingHandlerMapping getObject(Object... args) {
                return mappings[0];
            }

            @Override
            public RequestMappingHandlerMapping getIfAvailable() {
                return mappings.length == 1 ? mappings[0] : null;
            }

            @Override
            public RequestMappingHandlerMapping getIfUnique() {
                return mappings.length == 1 ? mappings[0] : null;
            }
        };
    }

    private static ObjectProvider<RequestMappingHandlerMapping> emptyProvider() {
        return providerOf();
    }

    // -------------------------------------------------------------------------------------------
    // Stand-in controllers. Not Spring beans — they are registered into the mapping by hand.
    // -------------------------------------------------------------------------------------------

    static final class Undeclared {
        public void handle() {
        }
    }

    @Requires(section = Section.PRODUCTS)
    static final class DeclaredOnClass {
        public void handle() {
        }
    }

    @Requires(section = Section.PRODUCTS, level = AccessLevel.NONE)
    static final class DeclaredWithNone {
        public void handle() {
        }
    }

    @Requires(section = Section.SALES_ORDER_FULFILLMENT)
    static final class DeclaredWithReservedSection {
        public void handle() {
        }
    }
}
