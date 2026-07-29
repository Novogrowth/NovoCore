package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * How long the outbox keeps things — Q43's two answers, read from Settings.
 *
 * <p>Its own type rather than two loose settings reads, because the pair has an invariant worth
 * stating in one place: <strong>a row must not outlive its attachment's usefulness in the wrong
 * direction.</strong> Keeping bytes longer than the row that explains them would leave orphaned
 * content, which the cascade prevents, but the reverse — a row kept forever whose inline copy went
 * at 90 days — is the intended arrangement and is what the history's "no longer available" state
 * exists to render.
 *
 * @param messageAge how long a sent message's row is kept, empty for forever
 * @param inlineAttachmentAge how long an inline copy is kept, empty for forever
 */
record EmailRetentionPolicy(Optional<Duration> messageAge, Optional<Duration> inlineAttachmentAge) {

    static EmailRetentionPolicy readFrom(SettingsService settings) {
        return new EmailRetentionPolicy(
                days(settings, SettingKeys.EMAIL_RETENTION_MESSAGE_DAYS),
                days(settings, SettingKeys.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS));
    }

    /** The instant before which a sent message's row may be removed, if ever. */
    Optional<Instant> messageCutoff(Instant now) {
        return messageAge.map(now::minus);
    }

    /** The instant before which a sent message's inline copies may be dropped, if ever. */
    Optional<Instant> inlineAttachmentCutoff(Instant now) {
        return inlineAttachmentAge.map(now::minus);
    }

    /**
     * Reads a retention setting as a number of days, or empty for {@code FOREVER}.
     *
     * <p>Refuses anything else rather than defaulting. A retention policy is one of the few
     * settings whose misreading destroys data that cannot be recovered, so an unparseable value
     * must stop the prune and say so — never be treated as "0 days", which would delete
     * everything, and never silently as "forever", which would hide that the setting is broken
     * until a disk filled up.
     */
    private static Optional<Duration> days(SettingsService settings, String key) {
        String value = settings.find(key)
                .map(String::trim)
                .orElseThrow(() -> new IllegalStateException(
                        "Setting '%s' is missing. It is seeded by migration; a database that lacks "
                                .formatted(key) + "it has not been migrated."));

        if (value.equalsIgnoreCase(SettingKeys.RETENTION_FOREVER)) {
            return Optional.empty();
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                // Zero would mean "delete as soon as it is sent", which nobody types on purpose
                // and which no amount of logging would undo.
                throw new IllegalStateException(
                        "Setting '%s' is '%s'. A retention period must be at least 1 day, or the "
                                .formatted(key, value) + "word " + SettingKeys.RETENTION_FOREVER
                                + ".");
            }
            return Optional.of(Duration.ofDays(parsed));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Setting '%s' is '%s', which is neither a number of days nor the word %s."
                            .formatted(key, value, SettingKeys.RETENTION_FOREVER), e);
        }
    }
}
