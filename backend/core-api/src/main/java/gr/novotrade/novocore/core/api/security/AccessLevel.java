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

    /**
     * Whether moving from {@code previous} to this level <strong>takes access away</strong>.
     *
     * <p>Read as {@code newLevel.isNarrowerThan(previousLevel)}. Equal levels are not narrower: a
     * grant rewritten to what it already was changes nothing, and must not be treated as a
     * revocation.
     *
     * <p><strong>This decides whether a role change ends its holders' sessions</strong>, which is
     * why it lives on the type rather than being an inline comparison in {@code RoleServiceImpl}.
     * The asymmetry is deliberate and load-bearing: taking access away has to take effect now,
     * because the case it exists for is cutting somebody off; <em>widening</em> a grant does not end
     * a session, because forcing a re-login on somebody who was just given more would be a penalty
     * for an administrator's kindness. See {@code UserSessions}.
     */
    public boolean isNarrowerThan(AccessLevel previous) {
        return rank() < previous.rank();
    }

    /**
     * The ordering, stated explicitly rather than taken from {@link #ordinal()}.
     *
     * <p>{@code ordinal()} would give the same answer today and would silently give a different one
     * the day somebody reorders the constants — and the thing that reads it decides whether a
     * revoked user stays logged in. That is not a failure worth risking to save three lines.
     */
    private int rank() {
        return switch (this) {
            case NONE -> 0;
            case VIEW -> 1;
            case FULL -> 2;
        };
    }
}
