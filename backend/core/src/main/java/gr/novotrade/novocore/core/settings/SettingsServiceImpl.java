package gr.novotrade.novocore.core.settings;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.settings.SettingNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingValueException;
import gr.novotrade.novocore.core.api.settings.SettingView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettingsServiceImpl implements SettingsService {

    private static final String ENTITY_TYPE = "Setting";

    private final SettingRepository repository;
    private final AuditLogService auditLog;

    SettingsServiceImpl(SettingRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> find(String key) {
        Objects.requireNonNull(key, "key");
        return repository.findById(key).map(Setting::getValue);
    }

    @Override
    @Transactional(readOnly = true)
    public String require(String key) {
        return find(key).orElseThrow(() -> new SettingNotFoundException(key));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> findDecimal(String key) {
        return find(key).map(value -> parseDecimal(key, value));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal requireDecimal(String key) {
        return parseDecimal(key, require(key));
    }

    @Override
    @Transactional(readOnly = true)
    public Money requireEurAmount(String key) {
        BigDecimal value = requireDecimal(key);
        try {
            return Money.of(value, Money.EUR);
        } catch (IllegalArgumentException e) {
            // Money rejects more than two decimals rather than rounding. Surfacing that as a
            // setting problem names the key, instead of leaving a bare arithmetic complaint.
            throw new SettingValueException(key, "a monetary amount", value.toPlainString(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean requireBoolean(String key) {
        String value = require(key).trim();
        // Strict: only these four spellings. Java's Boolean.parseBoolean treats every
        // unrecognised value as false, so a typo would silently disable a feature.
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")) {
            return false;
        }
        throw new SettingValueException(key, "a boolean (true/false/yes/no)", value, null);
    }

    @Override
    @Transactional(readOnly = true)
    public int requireInt(String key) {
        String value = require(key).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new SettingValueException(key, "an integer", value, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RoundingMode requireRoundingMode(String key) {
        String value = require(key).trim();
        try {
            return RoundingMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SettingValueException(key, "a java.math.RoundingMode name", value, e);
        }
    }

    @Override
    @Transactional
    public void put(String key, String value) {
        write(key, value, false);
    }

    @Override
    @Transactional
    public void putSecret(String key, String value) {
        write(key, value, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettingView> listRedacted() {
        return repository.findAllByOrderByKeyAsc().stream()
                .map(SettingsServiceImpl::toRedactedView)
                .toList();
    }

    private void write(String key, String value, boolean secret) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        Optional<Setting> existing = repository.findById(key);
        boolean isSecret = secret || existing.map(Setting::isSecret).orElse(false);

        Setting setting = existing.orElseGet(() -> new Setting(key, value, isSecret, null));
        boolean isNew = existing.isEmpty();
        if (!isNew) {
            setting.changeValue(value);
            if (secret) {
                setting.markSecret();
            }
        }
        repository.save(setting);

        // The value is recorded only when it is not a credential. An audit log that captures
        // SMTP passwords in plain text turns a control into a second place secrets leak from.
        Map<String, String> detail = isSecret
                ? Map.of("key", key, "secret", "true", "value", "<not recorded>")
                : Map.of("key", key, "secret", "false", "value", value);
        auditLog.record(isNew ? "setting.created" : "setting.updated", ENTITY_TYPE, key, detail);
    }

    private static BigDecimal parseDecimal(String key, String rawValue) {
        String value = rawValue.trim();
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            // The decimal-comma case this guards against is real: "0,03" is how the threshold
            // would plausibly be typed here, and it parses as nothing.
            throw new SettingValueException(key, "a decimal number (use '.' not ',')", value, e);
        }
    }

    private static SettingView toRedactedView(Setting setting) {
        return new SettingView(
                setting.getKey(),
                setting.isSecret() ? SettingView.REDACTED : setting.getValue(),
                setting.isSecret(),
                setting.getDescription(),
                setting.getUpdatedAt(),
                setting.getUpdatedBy());
    }
}
