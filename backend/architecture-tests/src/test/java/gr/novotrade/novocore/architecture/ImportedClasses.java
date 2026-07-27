package gr.novotrade.novocore.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Imports NovoCore's production class graph once and shares it across the rule tests.
 *
 * <p>This holder is why we do not need {@code archunit-junit5} and its
 * {@code @AnalyzeClasses} caching — see ADR 0002 for why depending on that artifact was
 * rejected. A static field gives the same "import the graph once per JVM" benefit in five
 * lines.
 *
 * <p>Note the absence of {@code DO_NOT_INCLUDE_JARS}. It is tempting, but it would be a
 * quiet correctness bug: during a full reactor build the other modules resolve to
 * {@code target/classes} directories and it would make no difference, but when this module
 * is built against installed jars it would exclude the very classes the rules exist to
 * check, and every rule would pass vacuously. {@link ArchUnitSanityTest} guards against
 * that class of failure regardless.
 */
final class ImportedClasses {

    static final String ROOT_PACKAGE = "gr.novotrade.novocore";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    static JavaClasses production() {
        return PRODUCTION_CLASSES;
    }

    /** Counts imported production classes whose package starts with {@code packagePrefix}. */
    static long countInPackage(String packagePrefix) {
        long count = 0;
        for (JavaClass javaClass : PRODUCTION_CLASSES) {
            if (javaClass.getPackageName().equals(packagePrefix)
                    || javaClass.getPackageName().startsWith(packagePrefix + ".")) {
                count++;
            }
        }
        return count;
    }

    private ImportedClasses() {
    }
}
