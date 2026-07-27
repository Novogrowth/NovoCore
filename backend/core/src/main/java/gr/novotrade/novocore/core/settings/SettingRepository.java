package gr.novotrade.novocore.core.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.settings.SettingsService}.
 */
interface SettingRepository extends JpaRepository<Setting, String> {

    List<Setting> findAllByOrderByKeyAsc();
}
