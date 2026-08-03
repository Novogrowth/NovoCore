package gr.novotrade.novocore.core.api.settings;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.time.Instant;
import java.util.Objects;

/**
 * A setting as it is safe to show. For a secret, {@link #value} is {@link #REDACTED} rather
 * than the real credential — redaction happens here, in the only type the API can return, so
 * it cannot be forgotten at a call site.
 *
 * @param value the stored value, or {@link #REDACTED} when {@link #secret} is true
 */
public record SettingView(
        @Mandatory String key,
        @Mandatory String value,
        boolean secret,
        String description,
        Instant updatedAt,
        String updatedBy) {

    public static final String REDACTED = "********";

    public SettingView {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }

    /** True when a secret has never been given a value, so a feature depending on it cannot run. */
    public boolean isUnset() {
        return value.isBlank() || (secret && REDACTED.equals(value) && updatedAt == null);
    }
}
