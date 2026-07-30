package gr.novotrade.novocore.core.api.settings;

import java.util.List;

/**
 * Reading and changing the settings the API exposes — the operator-facing half of
 * {@link SettingsService}.
 *
 * <h2>Two services, and the split is the point</h2>
 *
 * <p>{@link SettingsService} is what the <em>core</em> uses: untyped, unrestricted, every key. This is
 * what a <em>route</em> uses: only {@link SettingsCatalog}'s keys, validated on write, secrets never
 * returned. They are separate interfaces rather than extra methods on one, because the guarantee is
 * "the web layer cannot reach the other one" — and an interface a controller has no reference to is a
 * stronger statement than a method it has been asked not to call.
 *
 * <p>An ArchUnit rule holds the boundary: no class in {@code ..core.web..} may depend on
 * {@code SettingsService}. Without it, a future controller reaching for {@code settings.put(...)}
 * directly would bypass the catalogue, the validation and the exclusion in one line that looks
 * entirely reasonable.
 */
public interface SettingsAdminService {

    /**
     * Every catalogued setting, in key order, with secrets never carrying a value.
     *
     * <p><strong>Not {@link SettingsService#listRedacted()}.</strong> That returns every row in the
     * table, which includes the Drive folder and client ids — not flagged secret, and therefore not
     * redacted by it. This returns the allowlist.
     */
    List<SettingView> list();

    /** One catalogued setting. Never carries a value for a write-only entry. */
    SettingView get(SettingsCatalog setting);

    /**
     * Replaces a value, after checking it is one this setting can hold.
     *
     * <p><strong>Validated before it is stored</strong>, so a refused write leaves the previous value
     * in place. That is the guarantee worth having: configuration that governs money never becomes
     * unreadable because somebody submitted a form with a comma in it.
     *
     * @throws SettingValueException if the value is not valid for this setting's type
     * @throws InvalidSettingException if this setting is not writable through the API
     */
    void put(SettingsCatalog setting, String value);
}
