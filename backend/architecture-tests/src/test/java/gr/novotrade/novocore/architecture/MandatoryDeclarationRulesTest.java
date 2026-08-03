package gr.novotrade.novocore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import gr.novotrade.novocore.core.api.shared.ConditionallyMandatory;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

/**
 * <strong>{@link Mandatory} says a component is never absent. This is what makes that true rather
 * than merely written.</strong> It reads the canonical constructor's bytecode and compares it with
 * the annotations, in both directions, on every build.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>Without it, {@code @Mandatory} is several hundred hand-applied assertions that nothing verifies
 * — and each of them is published to every client as a promise. That is {@code CLAUDE.md}'s <em>a
 * fact established by reading, then built upon</em>, at the scale of a whole API surface. The two
 * directions fail differently and both matter:
 *
 * <ul>
 *   <li><strong>Guarded but not declared</strong> — the contract calls a field optional that the
 *       server refuses to do without. A generated client makes it {@code T | undefined}, every caller
 *       is invited to omit it, and the failure arrives at runtime as a 400.
 *   <li><strong>Declared but not guarded</strong> — worse, because it is silent. The contract promises
 *       a field is always there, {@code tsc} lets every consumer dereference it without a check, and
 *       nothing anywhere refuses the body that omits it.
 * </ul>
 *
 * <h2>⚠️ ArchUnit cannot do this, and the mechanism is ASM plus reflection</h2>
 *
 * <p>This was designed as an "ArchUnit cross-check" and is not one. ArchUnit contributes class
 * discovery and the failure idiom; the attribution is done by {@code org.springframework.asm} (which
 * ships in {@code spring-core} and is already on this module's classpath) and by
 * {@link RecordComponent#getAnnotation}. Two measured reasons, both from 8a's Phase 0 on 2026-08-03:
 *
 * <ul>
 *   <li>{@code JavaCodeUnit.getMethodCallsFromSelf()} exposes origin, target and line number and
 *       <strong>no argument information</strong>. It can say <em>this constructor guards something</em>
 *       and never <em>which component</em> — and which component is the entire question.
 *   <li>It <strong>over-counts</strong>, attributing calls made inside a lambda declared in the
 *       constructor back to the constructor. {@code StockLevels} is the live case: ArchUnit reports
 *       <strong>342</strong> guard calls across the surface where the constructors' bytecode contains
 *       <strong>340</strong>, the difference being two {@code requireNonNull}s inside
 *       {@code byLocation.forEach(…)} that constrain map entries rather than components. This is the
 *       same blind spot {@code CLAUDE.md} already names under proxy self-invocation — <em>nor is a
 *       call reached through a lambda</em>.
 * </ul>
 *
 * <h2>What counts as a guard, stated precisely because the rule turns on it</h2>
 *
 * <p>An {@code INVOKESTATIC} to {@code Objects.requireNonNull}, {@link
 * gr.novotrade.novocore.core.api.shared.Required#field} or {@code Required.text}, inside the
 * <em>canonical constructor itself</em>, whose first argument is a <strong>direct load of a
 * canonical-constructor parameter slot</strong>. Three consequences, all deliberate:
 *
 * <ul>
 *   <li>A guard inside a lambda does not count — it constrains whatever the lambda receives.
 *   <li>A guard on a loop variable does not count; its slot is not a parameter slot.
 *       {@code NewFreightAllocation} checks each {@code lotId} in the list it was handed, which says
 *       nothing about whether {@code lotIds} itself may be absent.
 *   <li>A guard call whose argument cannot be attributed <strong>fails the build</strong> rather than
 *       being ignored. As of 2026-08-03 there are none; a new one means somebody wrote a guard in a
 *       shape this cannot read, and silently dropping it would quietly weaken the contract.
 * </ul>
 *
 * <h2>⚠️ The declared set is a LOWER BOUND on what is mandatory</h2>
 *
 * <p>Only those three forms are visible. A component made mandatory by an inline
 * {@code if (x == null) throw} is not — {@code EmailMessage.subject} and {@code EmailMessage.body}
 * are the known cases, and neither is on the HTTP surface. <strong>So the second direction must never
 * be read as "everything mandatory is annotated."</strong> It says only that nothing is <em>declared</em>
 * mandatory without the constructor refusing a null. An incomplete {@code required} list is still
 * true; a wrong one is worse than none.
 */
class MandatoryDeclarationRulesTest {

    private static final String OBJECTS = "java/util/Objects";
    private static final String REQUIRED = "gr/novotrade/novocore/core/api/shared/Required";

    private static final JavaClasses IMPORTED =
            new ClassFileImporter().importPackages("gr.novotrade.novocore");

    // -------------------------------------------------------------------------------------------
    // Direction 1 — a component the constructor refuses a null for must say so
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every guarded component is declared @Mandatory or @ConditionallyMandatory")
    void everyGuardedComponentIsDeclared() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> record : recordsOnTheClassGraph()) {
            Set<String> guarded = guardedComponentsOf(record);
            for (RecordComponent component : record.getRecordComponents()) {
                if (!guarded.contains(component.getName())) {
                    continue;
                }
                boolean declared = component.getAnnotation(Mandatory.class) != null
                        || component.getAnnotation(ConditionallyMandatory.class) != null;
                if (!declared) {
                    violations.add(record.getName() + "." + component.getName()
                            + " is refused when absent by the canonical constructor, but the "
                            + "contract describes it as optional. Add @Mandatory — or "
                            + "@ConditionallyMandatory with its reason, if the guard is conditional.");
                }
            }
        }
        assertThat(violations)
                .as("a component the server will not do without, published to every client as "
                        + "optional")
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------------
    // Direction 2 — a component that says it is never absent must be refused when absent
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every @Mandatory component is guarded by its canonical constructor")
    void everyDeclaredComponentIsGuarded() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> record : recordsOnTheClassGraph()) {
            Set<String> guarded = guardedComponentsOf(record);
            for (RecordComponent component : record.getRecordComponents()) {
                if (component.getAnnotation(Mandatory.class) == null) {
                    continue;
                }
                if (component.getType().isPrimitive()) {
                    violations.add(record.getName() + "." + component.getName()
                            + " is a primitive, which OpenApiSchema already marks required. "
                            + "@Mandatory here is a second statement of the same fact, free to "
                            + "disagree with the first.");
                } else if (!guarded.contains(component.getName())) {
                    violations.add(record.getName() + "." + component.getName()
                            + " is declared @Mandatory, so the contract promises it is always "
                            + "present — but the canonical constructor accepts a null for it, so "
                            + "nothing enforces the promise.");
                }
            }
        }
        assertThat(violations)
                .as("a contract promising what nothing refuses")
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------------
    // Direction 2, the other half — an exemption must have something to be exempt from
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("@ConditionallyMandatory marks a real guard, and says why it is conditional")
    void everyExemptionIsRealAndExplained() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> record : recordsOnTheClassGraph()) {
            Set<String> guarded = guardedComponentsOf(record);
            for (RecordComponent component : record.getRecordComponents()) {
                ConditionallyMandatory exemption =
                        component.getAnnotation(ConditionallyMandatory.class);
                if (exemption == null) {
                    continue;
                }
                if (!guarded.contains(component.getName())) {
                    violations.add(record.getName() + "." + component.getName()
                            + " is marked @ConditionallyMandatory but the canonical constructor "
                            + "does not guard it at all, so there is nothing to be exempt from.");
                }
                if (exemption.value().isBlank()) {
                    violations.add(record.getName() + "." + component.getName()
                            + " is marked @ConditionallyMandatory with no reason. The reason is the "
                            + "point: without it the next reader concludes somebody forgot.");
                }
                if (component.getAnnotation(Mandatory.class) != null) {
                    violations.add(record.getName() + "." + component.getName()
                            + " is both @Mandatory and @ConditionallyMandatory, which cannot both "
                            + "be true.");
                }
            }
        }
        assertThat(violations).as("an exemption with nothing behind it").isEmpty();
    }

    // -------------------------------------------------------------------------------------------
    // The extractor's own precondition
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every guard call in a canonical constructor can be attributed to a component")
    void everyGuardCallIsAttributable() throws Exception {
        List<Class<?>> records = recordsOnTheClassGraph();
        assertThat(records)
                .as("the class graph is empty, so both directions above are vacuous")
                .hasSizeGreaterThan(100);
        for (Class<?> record : records) {
            // Throws with the offending call named if a guard cannot be resolved to a parameter.
            guardedComponentsOf(record);
        }
    }

    // -------------------------------------------------------------------------------------------

    private static List<Class<?>> recordsOnTheClassGraph() {
        List<Class<?>> records = new ArrayList<>();
        for (JavaClass javaClass : IMPORTED) {
            if (javaClass.isRecord()) {
                records.add(javaClass.reflect());
            }
        }
        return records;
    }

    /**
     * The components this record's canonical constructor refuses a null for, read from that
     * constructor's own bytecode.
     *
     * @throws AssertionError if a guard call cannot be attributed to a parameter — see the class
     *                        javadoc for why that fails rather than being skipped
     */
    static Set<String> guardedComponentsOf(Class<?> record) throws IOException {
        RecordComponent[] components = record.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Map<Integer, String> parameterSlots = new LinkedHashMap<>();
        int slot = 1; // 0 is `this`
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            parameterSlots.put(slot, components[i].getName());
            slot += (parameterTypes[i] == long.class || parameterTypes[i] == double.class) ? 2 : 1;
        }

        Constructor<?> canonical;
        try {
            canonical = record.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(record.getName() + " has no canonical constructor, which a "
                    + "record cannot be without", e);
        }
        String descriptor = Type.getConstructorDescriptor(canonical);

        byte[] bytecode;
        String resource = "/" + record.getName().replace('.', '/') + ".class";
        try (InputStream in = record.getResourceAsStream(resource)) {
            bytecode = Objects.requireNonNull(in, resource).readAllBytes();
        }

        Set<String> guarded = new LinkedHashSet<>();
        List<String> unattributable = new ArrayList<>();

        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                if (!"<init>".equals(name) || !descriptor.equals(desc)) {
                    return null;
                }
                return new GuardVisitor(record, parameterSlots, guarded, unattributable);
            }
        }, ClassReader.SKIP_FRAMES);

        if (!unattributable.isEmpty()) {
            throw new AssertionError("A guard call in a canonical constructor could not be "
                    + "attributed to a component: " + unattributable + ". Write the guard as a "
                    + "direct call on the parameter — Required.field(x, \"x\") — so the contract "
                    + "can be derived from it, rather than leaving it to be guessed at.");
        }
        return new TreeSet<>(guarded);
    }

    /**
     * Records which parameter each guard call applies to.
     *
     * <p>The argument is identified by the load instruction immediately preceding the call rather
     * than by the guard's message string, and that is deliberate: six guards on this surface carry a
     * sentence instead of a field name ({@code "productId is required on an inventory line"}), and
     * one carries a name that is not a component at all. <strong>The slot is what the JVM will
     * actually check; the string is what a human will read.</strong>
     *
     * <p>A short instruction window is enough because a record's canonical constructor never reuses a
     * parameter slot — every parameter is live until the closing {@code PUTFIELD}s, and a compact
     * constructor's assignments write back into the same slot.
     */
    private static final class GuardVisitor extends MethodVisitor {

        /** How many instructions may sit between the load and the call and still be that argument. */
        private static final int WINDOW = 2;

        private final Class<?> record;
        private final Map<Integer, String> parameterSlots;
        private final Set<String> guarded;
        private final List<String> unattributable;

        private Integer lastLoadedSlot;
        private int instructionsSinceLoad = Integer.MAX_VALUE - 1;

        private GuardVisitor(Class<?> record, Map<Integer, String> parameterSlots,
                Set<String> guarded, List<String> unattributable) {
            super(Opcodes.ASM9);
            this.record = record;
            this.parameterSlots = parameterSlots;
            this.guarded = guarded;
            this.unattributable = unattributable;
        }

        private void step() {
            instructionsSinceLoad++;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ALOAD) {
                lastLoadedSlot = varIndex;
                instructionsSinceLoad = 0;
            } else {
                step();
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean isInterface) {
            if (isGuard(opcode, owner, name)) {
                if (instructionsSinceLoad > WINDOW || lastLoadedSlot == null) {
                    unattributable.add(record.getName() + " → " + owner + "." + name);
                } else {
                    // A slot that is not a parameter is a local — a loop variable, typically — and
                    // says nothing about a component. Deliberately not a violation.
                    String component = parameterSlots.get(lastLoadedSlot);
                    if (component != null) {
                        guarded.add(component);
                    }
                }
            }
            step();
        }

        private static boolean isGuard(int opcode, String owner, String name) {
            if (opcode != Opcodes.INVOKESTATIC) {
                return false;
            }
            return (OBJECTS.equals(owner) && "requireNonNull".equals(name))
                    || (REQUIRED.equals(owner) && ("field".equals(name) || "text".equals(name)));
        }

        @Override
        public void visitLdcInsn(Object value) {
            step();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            step();
        }

        @Override
        public void visitInsn(int opcode) {
            step();
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            step();
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            step();
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            step();
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc,
                org.springframework.asm.Handle handle, Object... arguments) {
            step();
        }
    }
}
