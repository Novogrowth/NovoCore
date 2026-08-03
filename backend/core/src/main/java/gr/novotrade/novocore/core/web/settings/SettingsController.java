package gr.novotrade.novocore.core.web.settings;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.settings.SettingView;
import gr.novotrade.novocore.core.api.settings.SettingsAdminService;
import gr.novotrade.novocore.core.api.settings.SettingsCatalog;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator-changeable configuration — the API {@link Section#SETTINGS} had no route for until step
 * 16b, when everything here was still direct SQL.
 *
 * <h2>{@code {key}} is an enum, and that is what makes the exclusion structural</h2>
 *
 * <p>The path variable binds to {@link SettingsCatalog}, so a key that is not on the allowlist is
 * refused by Spring <strong>before any of our code runs</strong>. The whole {@code backup.*}
 * namespace — the two Drive credentials per destination, and the folder and client ids that are not
 * flagged secret and would otherwise be returned in the clear — simply has no route. Not a check
 * somebody has to remember to write; a URL that does not exist.
 *
 * <p>The backup encryption key needs no exclusion at all: it is an environment variable with no row
 * in the settings table, because the table is inside the dump it decrypts (ADR 0013).
 * {@code SettingsCatalogTest} asserts that absence rather than trusting it.
 *
 * <h2>Read at VIEW, write at FULL, and the read is a real grant</h2>
 *
 * <p>Reading exposes the SMTP host and username, the rounding threshold and the retention policy —
 * not nothing. It is the section's purpose and it is default-deny, so no role holds it today.
 * Deliberately <em>not</em> hard-coded to Owner and Admin, for the reason the users and roles surface
 * is not: {@code Section.SETTINGS} exists for this, and a hard-coded check would make it dead code.
 */
@RestController
@Requires(section = Section.SETTINGS)
class SettingsController {

    private final SettingsAdminService settings;

    SettingsController(SettingsAdminService settings) {
        this.settings = settings;
    }

    /**
     * Every exposed setting, in key order.
     *
     * <p>The allowlist, not the table. A secret carries no value at all — not a redacted string with
     * a real timestamp beside it — so what a client learns about {@code smtp.password} is whether one
     * has ever been set.
     */
    @GetMapping(path = "/api/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SettingView> settings() {
        return ListResponse.of(settings.list());
    }

    @GetMapping(path = "/api/settings/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    SettingView setting(@PathVariable SettingsCatalog key) {
        return settings.get(key);
    }

    /**
     * Replaces a value, <strong>after checking the setting can hold it</strong>.
     *
     * <p>A refused write leaves the previous value in place. That is the guarantee: today
     * {@code SettingsService.put} accepts {@code "0,03"} with a comma quite happily, and the failure
     * arrives later on the next invoice somebody records — as an error about a key they were not
     * thinking about, raised by a document with nothing wrong with it.
     *
     * <p>204 rather than the updated setting: echoing it back would mean deciding what to return for
     * a write-only credential, and the only sensible answer is nothing.
     */
    @PutMapping(path = "/api/settings/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTINGS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void put(@PathVariable SettingsCatalog key, @RequestBody ValueRequest request) {
        settings.put(key, request.value());
    }

    // -------------------------------------------------------------------------------------------

    /**
     * @param value required and never blank. A blank is refused here rather than in the core, so the
     *     message is about the request; several types would accept it as a valid-looking empty
     *     string, and "the SMTP host is now nothing" is not a change anybody means to make.
     */
    record ValueRequest(@Mandatory String value) {

        ValueRequest {
            Required.text(value, "value");
        }
    }
}
