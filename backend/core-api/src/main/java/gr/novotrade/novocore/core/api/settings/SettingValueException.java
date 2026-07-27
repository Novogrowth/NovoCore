package gr.novotrade.novocore.core.api.settings;

/**
 * A setting exists but its stored value cannot be read as the requested type.
 *
 * <p>Deliberately not a silent fallback to a default. A rounding threshold entered as
 * "0,03" with a decimal comma would parse as nothing; quietly treating that as zero would
 * change how every rounding difference is handled without anyone being told.
 */
public class SettingValueException extends RuntimeException {

    private final String key;

    public SettingValueException(String key, String expectedType, String actualValue,
            Throwable cause) {
        super("Setting '%s' cannot be read as %s. Stored value: '%s'."
                .formatted(key, expectedType, actualValue), cause);
        this.key = key;
    }

    public String key() {
        return key;
    }
}
