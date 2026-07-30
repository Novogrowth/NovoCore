package gr.novotrade.novocore.core.settings;

import gr.novotrade.novocore.core.api.settings.InvalidSettingException;
import gr.novotrade.novocore.core.api.settings.SettingView;
import gr.novotrade.novocore.core.api.settings.SettingsAdminService;
import gr.novotrade.novocore.core.api.settings.SettingsCatalog;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The operator-facing settings surface, restricted to {@link SettingsCatalog}.
 *
 * <p>Reads go through the repository rather than through {@code SettingsService.listRedacted()},
 * because that returns every row in the table — including the two Drive values per destination that
 * are not flagged secret and would come back in the clear.
 */
@Service
class SettingsAdminServiceImpl implements SettingsAdminService {

    private final SettingRepository repository;

    /**
     * The interface, not the implementation — so writes go through Spring's proxy and pick up the
     * audit entry {@code SettingsServiceImpl.write} records. A different bean, so this is not the
     * self-invocation trap; it is the ordinary collaboration that trap is fixed by.
     */
    private final SettingsService settings;

    SettingsAdminServiceImpl(SettingRepository repository, SettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettingView> list() {
        return Arrays.stream(SettingsCatalog.values())
                .sorted(Comparator.comparing(SettingsCatalog::key))
                .map(this::viewOf)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SettingView get(SettingsCatalog setting) {
        Objects.requireNonNull(setting, "setting");
        return viewOf(setting);
    }

    @Override
    @Transactional
    public void put(SettingsCatalog setting, String value) {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(value, "value");

        if (!setting.isWritable()) {
            throw new InvalidSettingException(
                    "'" + setting.key() + "' can be read but not changed here. It is a statutory "
                            + "limit rather than a preference, and a screen that raised it would "
                            + "make breaking the law a two-click operation. Changing it is a "
                            + "deliberate act by somebody who knows the legal position.");
        }

        // Before the write, never after: a refused value must leave the previous one in place, or a
        // typo in a form makes configuration that governs money unreadable until somebody notices
        // the next document failing for a reason that has nothing to do with it.
        setting.validate(value);

        if (setting.isSecret()) {
            settings.putSecret(setting.key(), value);
        } else {
            settings.put(setting.key(), value);
        }
    }

    /**
     * <strong>A write-only setting never carries a value</strong>, not even a redacted one with a
     * real {@code updatedAt} beside it — the fact that it is set is reported by
     * {@link SettingView#isUnset()} and nothing more.
     */
    private SettingView viewOf(SettingsCatalog entry) {
        Optional<Setting> stored = repository.findById(entry.key());
        if (stored.isEmpty()) {
            // A catalogued key with no row is the ordinary state of smtp.password before anybody
            // sets one, and SettingsCatalogTest refuses it for every other entry.
            return new SettingView(entry.key(), "", entry.isSecret(), null, null, null);
        }
        Setting setting = stored.get();
        String value = entry.isSecret() || setting.isSecret()
                ? SettingView.REDACTED
                : setting.getValue();
        return new SettingView(
                entry.key(),
                value,
                entry.isSecret() || setting.isSecret(),
                setting.getDescription(),
                setting.getUpdatedAt(),
                setting.getUpdatedBy());
    }
}
