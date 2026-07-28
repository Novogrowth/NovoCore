package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.settings.SettingNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingValueException;
import gr.novotrade.novocore.core.api.settings.SettingView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link SettingsService} for the unit tests that exercise configuration reading
 * without a database.
 *
 * <p>A hand-written fake rather than a mock, because these tests care about behaviour across
 * several keys at once — what happens when one of eleven is missing — and stubbing that with
 * per-call expectations obscures the case being described.
 */
class FakeSettingsService implements SettingsService {

    private final Map<String, String> values = new LinkedHashMap<>();

    FakeSettingsService set(String key, String value) {
        values.put(key, value);
        return this;
    }

    FakeSettingsService remove(String key) {
        values.remove(key);
        return this;
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public String require(String key) {
        return find(key).orElseThrow(() -> new SettingNotFoundException(key));
    }

    @Override
    public Optional<BigDecimal> findDecimal(String key) {
        return find(key).map(BigDecimal::new);
    }

    @Override
    public BigDecimal requireDecimal(String key) {
        return new BigDecimal(require(key));
    }

    @Override
    public Money requireEurAmount(String key) {
        return Money.of(requireDecimal(key), Money.EUR);
    }

    @Override
    public boolean requireBoolean(String key) {
        return Boolean.parseBoolean(require(key));
    }

    @Override
    public int requireInt(String key) {
        String value = require(key).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new SettingValueException(key, "an integer", value, e);
        }
    }

    @Override
    public RoundingMode requireRoundingMode(String key) {
        return RoundingMode.valueOf(require(key));
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public void putSecret(String key, String value) {
        values.put(key, value);
    }

    @Override
    public List<SettingView> listRedacted() {
        throw new UnsupportedOperationException("not needed by these tests");
    }
}
