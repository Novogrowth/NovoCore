package gr.novotrade.novocore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces {@code CLAUDE.md} rule 5 — money is always {@code BigDecimal}, never
 * {@code double} or {@code float}. Called out there as the single most important rule in the
 * codebase, and stated with no exceptions: not in DTOs, not in reports, not in tests.
 *
 * <p>Rather than trying to identify which values are "money", these rules ban floating-point
 * types outright across NovoCore's own packages. That is a deliberately blunter rule than the
 * one written down, and it is blunter in the safe direction. Every quantity the domain deals
 * in — amounts, quantities, unit costs, depreciation rates, VAT rates, allocation
 * proportions — is a value where binary floating point silently loses precision, and the
 * failure is invisible: nothing throws, a total is simply wrong by a cent. Allowing
 * {@code double} anywhere means the next person has to correctly decide whether their
 * particular field counts as money, and one wrong answer is a real financial error.
 *
 * <p>If a genuine non-financial need for floating point ever appears, the right move is to
 * narrow these rules to the specific class with a stated reason, not to delete them.
 */
class MoneyRulesTest {

    private static final Set<String> FLOATING_POINT_TYPE_NAMES = Set.of(
            "double", "float", "java.lang.Double", "java.lang.Float");

    private static boolean isFloatingPoint(JavaClass type) {
        return FLOATING_POINT_TYPE_NAMES.contains(type.getName());
    }

    @Test
    @DisplayName("no field anywhere in NovoCore is float, double, Float or Double (rule 5)")
    void noFloatingPointFields() {
        ArchCondition<JavaField> beFloatingPoint =
                new ArchCondition<>("be of a floating-point type") {
                    @Override
                    public void check(JavaField field, ConditionEvents events) {
                        boolean violates = isFloatingPoint(field.getRawType());
                        events.add(new SimpleConditionEvent(field, violates,
                                "%s is of type %s".formatted(
                                        field.getFullName(), field.getRawType().getName())));
                    }
                };

        ArchRule rule = noFields()
                .should(beFloatingPoint)
                .because("CLAUDE.md rule 5: money is always BigDecimal. Stored state is the "
                        + "worst place for a float, because the imprecision persists and "
                        + "compounds across every later calculation.")
                // At skeleton stage NovoCore declares no fields at all, and ArchUnit treats a
                // rule that matched nothing as a failure. Remove this line in build step 2,
                // when Money and the first entities land and the rule has something to check.
                .allowEmptyShould(true);

        rule.check(ImportedClasses.production());
    }

    @Test
    @DisplayName("no method or constructor signature uses floating point (rule 5)")
    void noFloatingPointInSignatures() {
        ArchCondition<JavaCodeUnit> useFloatingPoint =
                new ArchCondition<>("use a floating-point type in its signature") {
                    @Override
                    public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                        for (JavaClass parameterType : codeUnit.getRawParameterTypes()) {
                            if (isFloatingPoint(parameterType)) {
                                events.add(SimpleConditionEvent.satisfied(codeUnit,
                                        "%s takes a %s parameter".formatted(
                                                codeUnit.getFullName(),
                                                parameterType.getName())));
                            }
                        }
                        if (isFloatingPoint(codeUnit.getRawReturnType())) {
                            events.add(SimpleConditionEvent.satisfied(codeUnit,
                                    "%s returns %s".formatted(
                                            codeUnit.getFullName(),
                                            codeUnit.getRawReturnType().getName())));
                        }
                    }
                };

        ArchRule rule = noCodeUnits()
                .should(useFloatingPoint)
                .because("CLAUDE.md rule 5: a float in a signature is how imprecision crosses "
                        + "a boundary. A BigDecimal field with a double setter is not "
                        + "compliant, it is just harder to spot.");

        rule.check(ImportedClasses.production());
    }

    @Test
    @DisplayName("nothing parses, boxes or converts through Double or Float (rule 5)")
    void noDependenciesOnFloatingPointWrappers() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Double")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Float")
                // Plain ASCII in rule messages: these strings are echoed into CI logs and
                // console output, where a non-ASCII dash renders as mojibake.
                .because("catches the routes the signature rules miss, such as "
                        + "Double.parseDouble on an imported figure or a Double sneaking "
                        + "through a collection's type argument. Parse into BigDecimal via "
                        + "its String constructor instead: BigDecimal.valueOf(double) "
                        + "inherits the error the double already has.");

        rule.check(ImportedClasses.production());
    }
}
