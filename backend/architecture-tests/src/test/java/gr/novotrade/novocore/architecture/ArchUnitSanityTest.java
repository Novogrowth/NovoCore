package gr.novotrade.novocore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the guardrails.
 *
 * <p>Every rule in this module is expressed as "no class should ...". Such a rule passes
 * trivially when no classes were imported, so a silent import failure would turn the entire
 * architecture suite green while enforcing nothing — the single worst failure mode available
 * to us, because {@code CLAUDE.md} rule 3 depends on this suite actually running.
 *
 * <p>Two ways that could happen, both covered here: an import option that accidentally
 * excludes our own artifacts, and ArchUnit being unable to parse the bytecode at all. The
 * second is a live concern rather than a hypothetical — ArchUnit 1.4.2 predates Java 25, and
 * Java 25 emits class file major version 69 (see ADR 0002). If ArchUnit's bundled ASM cannot
 * read it, this test fails loudly here instead of quietly everywhere else.
 */
class ArchUnitSanityTest {

    @Test
    @DisplayName("ArchUnit can parse Java 25 bytecode and imported a non-empty class graph")
    void importedClassGraphIsNotEmpty() {
        assertThat(ImportedClasses.production())
                .as("ArchUnit imported no classes from %s at all. Either the import options "
                        + "exclude our own artifacts, or ArchUnit cannot read Java 25 class "
                        + "files. Every rule in this module is vacuous until this passes.",
                        ImportedClasses.ROOT_PACKAGE)
                .isNotEmpty();
    }

    @Test
    @DisplayName("the application entry point is visible to the importer")
    void applicationClassWasImported() {
        boolean found = false;
        for (JavaClass javaClass : ImportedClasses.production()) {
            if (javaClass.getName().equals("gr.novotrade.novocore.NovoCoreApplication")) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .as("NovoCoreApplication was not imported, so the class graph is incomplete "
                        + "and the boundary rules are not seeing everything they should.")
                .isTrue();
    }

    /**
     * The modules whose classes the boundary rules actually constrain must be present.
     *
     * <p>At skeleton stage {@code core} and {@code core-api} contain only their
     * {@code package-info}, so this asserts presence rather than a meaningful count. Raise
     * these to real thresholds as the domain lands — a rule that can only ever see one
     * annotation-free class is not yet proving much.
     */
    @Test
    @DisplayName("core and core-api are both on the imported class path")
    void coreModulesAreVisible() {
        assertThat(ImportedClasses.countInPackage("gr.novotrade.novocore.core.api"))
                .as("no classes imported from core-api")
                .isGreaterThan(0);
        assertThat(ImportedClasses.countInPackage("gr.novotrade.novocore.core"))
                .as("no classes imported from core")
                .isGreaterThan(0);
    }
}
