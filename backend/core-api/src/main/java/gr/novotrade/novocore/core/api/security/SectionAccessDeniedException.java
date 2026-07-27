package gr.novotrade.novocore.core.api.security;

/**
 * A role tried to reach a section it has no access to.
 *
 * <p>The message names the role, the section and what was needed, because the person who has to
 * act on this is an administrator granting access — not the user who hit it. It deliberately says
 * nothing about the contents of the section.
 */
public class SectionAccessDeniedException extends RuntimeException {

    private final Section section;
    private final AccessLevel required;

    public SectionAccessDeniedException(String roleName, Section section, AccessLevel required) {
        super("Role '" + roleName + "' needs " + required + " access to " + section
                + " but has none.");
        this.section = section;
        this.required = required;
    }

    public Section section() {
        return section;
    }

    public AccessLevel required() {
        return required;
    }
}
