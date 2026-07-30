package gr.novotrade.novocore.core.api.shared;

/**
 * Which way a sorted list runs.
 *
 * <p>An enum rather than a boolean or a string. {@code ascending = false} reads as nothing at the
 * call site, and a string would have to be validated somewhere — which is the same argument that
 * made {@code @Requires} take a {@link gr.novotrade.novocore.core.api.security.Section} constant
 * rather than an expression: a value that cannot be misspelled does not need checking.
 */
public enum SortDirection {

    ASC,
    DESC;

    public boolean isDescending() {
        return this == DESC;
    }
}
