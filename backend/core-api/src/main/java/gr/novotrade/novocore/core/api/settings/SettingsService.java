package gr.novotrade.novocore.core.api.settings;

import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Operator-changeable configuration, stored once and read through here by everything that
 * needs it.
 *
 * <p>This exists because of a specific failure mode {@code CLAUDE.md} calls out: SMTP
 * credentials configured inside whichever module happened to need email first, then again in
 * the next one. Credentials and thresholds live here, and features ask for them rather than
 * holding their own copies.
 *
 * <p>Values are stored as text and parsed on read. A value that cannot be parsed as the
 * requested type throws rather than falling back to a default — a rounding threshold that
 * silently reverted to zero because someone typed "0,03" with a comma would change financial
 * behaviour without saying so.
 */
public interface SettingsService {

    Optional<String> find(String key);

    /**
     * @throws SettingNotFoundException if absent. Use for settings a feature genuinely cannot
     *     run without, so the failure names the missing key instead of surfacing later as a
     *     null.
     */
    String require(String key);

    Optional<BigDecimal> findDecimal(String key);

    BigDecimal requireDecimal(String key);

    /** Reads a EUR monetary amount, e.g. the rounding threshold or the cash payment limit. */
    Money requireEurAmount(String key);

    boolean requireBoolean(String key);

    int requireInt(String key);

    RoundingMode requireRoundingMode(String key);

    /**
     * Creates or replaces a value. Recorded in the audit log; for a setting marked secret, the
     * fact of the change is recorded but never the value.
     */
    void put(String key, String value);

    /** Creates or replaces a credential. Equivalent to {@link #put} with the secret flag set. */
    void putSecret(String key, String value);

    /** Every setting, with secret values redacted. Safe to return from an API. */
    List<SettingView> listRedacted();
}
