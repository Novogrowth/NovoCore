package gr.novotrade.novocore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A statutory codification has no create path — asserted, not merely intended.
 *
 * <h2>Why this is a rule and not a convention</h2>
 *
 * <p>{@code StatutoryCodification} is an interface whose entire content is a method that is
 * <em>absent</em>. That is a strange thing to express in Java and an easy one to undo: adding
 * {@code create} to an implementor compiles, runs, and looks exactly like every other reference-data
 * service in this codebase. Nothing else would notice, and the rows in question are transmitted to
 * the tax authority — a code somebody typed into a form is a compliance defect rather than a
 * data-entry mistake.
 *
 * <p>⚠️ <strong>The counter-argument, recorded because it was the reason for defining a contract at
 * all:</strong> Q1-b could have been closed by simply deleting {@code VatExemptionReasonService.create}
 * on the grounds that nothing called it. That would have left the next codification to rediscover
 * the argument from scratch, and would have left nothing to notice when somebody added one back.
 *
 * <h2>What it forbids, and why the line is drawn exactly there</h2>
 *
 * <p>It forbids any <strong>public</strong> method on an implementor whose name begins with
 * {@code create}, {@code add}, {@code register} or {@code insert} — the four verbs a well-meaning
 * author reaches for. It does <em>not</em> forbid {@code describe}, {@code deactivate} or
 * {@code reactivate}, which the contract grants, and it does not look at the persistence layer:
 * {@code JpaRepository.save} exists on every repository in the codebase and forbidding it here
 * would be a rule that fires on the seed itself.
 *
 * <p>⚠️ <strong>What it therefore cannot see, so watch for it in review:</strong> a method with an
 * innocent name that creates a row anyway — {@code adopt}, {@code recordCode}, {@code ensure}. The
 * naming rule is a tripwire on the obvious path, not a proof. The stronger guarantee is structural
 * and lives in the entities: {@code AadeInvoiceType} has no constructor other than JPA's, so there
 * is no Java expression that brings a row into existence at all.
 */
class StatutoryCodificationRulesTest {

    private static final String CONTRACT =
            "gr.novotrade.novocore.core.api.codification.StatutoryCodification";

    /** The verbs somebody adding a create path would actually use. */
    private static final List<String> CREATION_PREFIXES = List.of("create", "add", "register",
            "insert");

    @Test
    @DisplayName("the codification contract itself declares no way to author a row")
    void theContractHasNoCreateMethod() {
        JavaClass contract = ImportedClasses.production().get(CONTRACT);

        List<String> offenders = contract.getMethods().stream()
                .map(JavaMethod::getName)
                .filter(StatutoryCodificationRulesTest::looksLikeCreation)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("StatutoryCodification exists to say that rows come from Flyway and from "
                        + "nowhere else. A create method on the contract itself would grant every "
                        + "implementor the thing the contract is for denying.")
                .isEmpty();
    }

    @Test
    @DisplayName("no implementor of the codification contract offers one either")
    void noImplementorAddsACreateMethod() {
        JavaClass contract = ImportedClasses.production().get(CONTRACT);
        List<JavaClass> implementors = implementorsOf(contract);

        // ⚠️ The negative control for this test. With no implementors it would pass while measuring
        // nothing — the exact shape CLAUDE.md names under "the throwaway probe": a probe with only
        // positive cases cannot distinguish "the thing works" from "the thing was never there".
        assertThat(implementors)
                .as("no class implements StatutoryCodification, so this rule is passing vacuously "
                        + "and proving nothing. Either the contract was removed or the class graph "
                        + "does not include core-api.")
                .isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (JavaClass implementor : implementors) {
            for (JavaMethod method : implementor.getMethods()) {
                if (method.getModifiers().contains(
                        com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)
                        && looksLikeCreation(method.getName())) {
                    offenders.add(implementor.getName() + "." + method.getName());
                }
            }
        }

        assertThat(new TreeSet<>(offenders))
                .as("These implement StatutoryCodification and offer a way to author a row. The "
                        + "rows are a tax authority's, transmitted on documents, and a wrong one is "
                        + "a compliance defect rather than a data-entry mistake — so a new code is "
                        + "a migration with the artefact it was read from beside it, not an API "
                        + "call. If the list genuinely stopped being the authority's, move it out "
                        + "of the contract deliberately rather than adding a method back.")
                .isEmpty();
    }

    @Test
    @DisplayName("the two known codifications are both under the contract")
    void theKnownCodificationsAdoptIt() {
        JavaClass contract = ImportedClasses.production().get(CONTRACT);
        List<String> names = implementorsOf(contract).stream()
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        // Stated by name rather than by count. A count says "two of something" and cannot say
        // WHICH two came back, which is the lesson 8a's gate 3 paid for — seven boolean flags were
        // asserted by name for exactly this reason.
        //
        // ⚠️ This is deliberately an "at least" assertion. A third codification is a good thing and
        // should not fail the build; a MISSING one is what would be silent, because a service that
        // quietly stops implementing the contract loses its create-path guard with no other symptom.
        assertThat(names).contains("AadeInvoiceTypeService", "VatExemptionReasonService");
    }

    @Test
    @DisplayName("the business's document lists are deliberately NOT under the contract")
    void theBusinessListsAreNotCodifications() {
        JavaClass contract = ImportedClasses.production().get(CONTRACT);
        List<String> names = implementorsOf(contract).stream()
                .map(JavaClass::getSimpleName)
                .toList();

        // ⚠️ The rule in the other direction, and it is the one R1a exists because of. An earlier
        // design put the document type lists here, seeded from AADE with the code as the row's
        // identity. The owner's real Prosvasis Go configuration disproved it: six of his nineteen
        // types have NO AADE invoice type at all, because they are operational documents rather
        // than tax documents, and he needs to author more.
        //
        // Putting them back under the contract would compile and would remove his ability to add a
        // document type — a regression with no failing test anywhere unless it is stated here.
        assertThat(names)
                .as("These are the business's own lists with full CRUD, not AADE's. See "
                        + "StatutoryCodification's javadoc for what disproved the earlier model.")
                .doesNotContain(
                        "SalesDocumentTypeService", "PurchaseDocumentTypeService",
                        "SalesDocumentSeriesService", "PurchaseDocumentSeriesService",
                        "DeliveryMethodService", "ChargeTypeService", "VatClassService",
                        "UnitOfMeasureService");
    }

    private static List<JavaClass> implementorsOf(JavaClass contract) {
        List<JavaClass> implementors = new ArrayList<>();
        for (JavaClass candidate : ImportedClasses.production()) {
            if (!candidate.equals(contract) && candidate.isAssignableTo(CONTRACT)) {
                implementors.add(candidate);
            }
        }
        return implementors;
    }

    private static boolean looksLikeCreation(String methodName) {
        String lower = methodName.toLowerCase(Locale.ROOT);
        return CREATION_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
