package gr.novotrade.novocore.core.api.settings;

/**
 * A required setting is absent. Thrown rather than returning a default, so the failure names
 * the key that needs configuring instead of appearing later as unexplained behaviour.
 */
public class SettingNotFoundException extends RuntimeException {

    private final String key;

    public SettingNotFoundException(String key) {
        super("Setting '%s' is not configured. Set it under Settings before using the feature "
                .formatted(key) + "that requires it.");
        this.key = key;
    }

    public String key() {
        return key;
    }
}
