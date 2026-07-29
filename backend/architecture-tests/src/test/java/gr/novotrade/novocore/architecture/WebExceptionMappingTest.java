package gr.novotrade.novocore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every exception {@code core-api} can throw is mapped to an HTTP status.
 *
 * <p><strong>Why this test exists.</strong> {@code WebExceptionHandler} names every exception
 * explicitly, because all of them extend {@code RuntimeException} directly and there is no hierarchy
 * to match on. An explicit list is right — it makes each status a decision somebody made rather than
 * a consequence of a naming convention — but it has one failure mode: a new exception added to
 * {@code core-api} falls through to a 500 and nobody notices, because 500 is what an unmapped
 * exception has always produced.
 *
 * <p>So the list is checked rather than trusted. A new exception fails this test on the day it is
 * written, with the file that needs editing named in the message.
 *
 * <p>It deliberately does <em>not</em> assert which status each maps to. That would be a second copy
 * of the mapping, and a test that restates the code it checks fails whenever the code is right and
 * has been changed on purpose.
 */
class WebExceptionMappingTest {

    private static final String HANDLER = "gr.novotrade.novocore.core.web.WebExceptionHandler";
    private static final String EXCEPTION_HANDLER_ANNOTATION =
            "org.springframework.web.bind.annotation.ExceptionHandler";
    private static final String CORE_API_PACKAGE = "gr.novotrade.novocore.core.api";

    @Test
    @DisplayName("no core-api exception falls through to a 500")
    void everyCoreApiExceptionIsMapped() {
        Set<String> mapped = mappedExceptionNames();

        assertThat(mapped)
                .as("the handler must actually declare some @ExceptionHandler methods — if this is "
                        + "empty the test is passing vacuously and proving nothing")
                .isNotEmpty();

        List<String> unmapped = new ArrayList<>();
        for (JavaClass candidate : ImportedClasses.production()) {
            if (!isCoreApiException(candidate)) {
                continue;
            }
            if (!mapped.contains(candidate.getFullName())) {
                unmapped.add(candidate.getFullName());
            }
        }

        assertThat(new TreeSet<>(unmapped))
                .as("These exceptions are thrown by core-api and are not mapped in "
                        + "WebExceptionHandler, so a controller throwing one answers 500 with a "
                        + "stack trace instead of a status the caller can act on. Add each to the "
                        + "@ExceptionHandler group that matches what it means: 404 for 'no such "
                        + "record', 422 for 'the domain refuses this', 409 for 'conflicts with the "
                        + "record's current state'.")
                .isEmpty();
    }

    private static boolean isCoreApiException(JavaClass candidate) {
        return candidate.getPackageName().startsWith(CORE_API_PACKAGE)
                && candidate.getSimpleName().endsWith("Exception")
                && candidate.isAssignableTo(RuntimeException.class)
                && !candidate.getModifiers().contains(
                        com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT);
    }

    private static Set<String> mappedExceptionNames() {
        Set<String> mapped = new HashSet<>();
        JavaClass handler = ImportedClasses.production().get(HANDLER);
        for (JavaMethod method : handler.getMethods()) {
            method.tryGetAnnotationOfType(EXCEPTION_HANDLER_ANNOTATION).ifPresent(annotation ->
                    annotation.get("value").ifPresent(value -> {
                        if (value instanceof JavaClass[] handled) {
                            for (JavaClass exception : handled) {
                                mapped.add(exception.getFullName());
                            }
                        }
                    }));
        }
        return mapped;
    }
}
