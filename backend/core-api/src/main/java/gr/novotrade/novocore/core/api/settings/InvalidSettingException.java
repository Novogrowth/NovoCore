package gr.novotrade.novocore.core.api.settings;

/**
 * A setting cannot be changed the way the caller asked.
 *
 * <p>Distinct from {@link SettingValueException}, which means the <em>value</em> is wrong for the
 * type. This means the <em>operation</em> is refused whatever the value: today, that a setting is
 * exposed read-only because it is a statutory limit rather than a preference.
 *
 * <p>Its own type rather than an {@code IllegalArgumentException}, for the reason {@code CLAUDE.md}
 * names: an exception meaning "our code is wrong" used to tell a caller their request is wrong gets
 * its message correctly discarded, and the caller receives a bare {@code 400 "Bad request."}
 * {@code WebExceptionMappingTest} forces every {@code core-api} exception to be mapped, which is what
 * makes adding one here safe.
 */
public class InvalidSettingException extends RuntimeException {

    public InvalidSettingException(String message) {
        super(message);
    }
}
