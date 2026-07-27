package gr.novotrade.novocore.core.api.security;

/**
 * How much access a role has to a {@link Section}.
 *
 * <p>Three levels, not a set of verbs. Brief §7 describes access in exactly these terms —
 * full access, view-only, invisible — and a finer-grained create/read/update/delete matrix would
 * be four times the configuration to express distinctions nobody has asked for.
 */
public enum AccessLevel {

    /**
     * Invisible. The section is not listed, and a request against it is refused.
     *
     * <p>The default for anything a role has not been explicitly granted.
     */
    NONE,

    /** Readable, not changeable. */
    VIEW,

    /** Readable and changeable. */
    FULL;

    public boolean allowsView() {
        return this != NONE;
    }

    public boolean allowsEdit() {
        return this == FULL;
    }
}
