package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.time.Duration;

/**
 * How long to wait before attempting a message again.
 *
 * <p>Exponential, doubling each time from {@code base} and capped at {@code max}. Exponential
 * rather than a fixed interval because the two things that actually go wrong have opposite
 * shapes: a mail server restarting is over in seconds and wants a quick second attempt, while a
 * server refusing connections for an hour wants the attempts spread out rather than several
 * hundred identical failures written to the log.
 *
 * @param base delay before the second attempt
 * @param max ceiling on the doubling
 */
record RetryPolicy(Duration base, Duration max) {

    private static final Duration DEFAULT_BASE = Duration.ofSeconds(30);
    private static final Duration DEFAULT_MAX = Duration.ofMinutes(15);

    RetryPolicy {
        if (base.isNegative() || base.isZero()) {
            throw new IllegalArgumentException("Retry backoff must be positive, was " + base);
        }
        if (max.compareTo(base) < 0) {
            throw new IllegalArgumentException(
                    "Retry backoff ceiling %s is below the base delay %s".formatted(max, base));
        }
    }

    static RetryPolicy readFrom(SettingsService settings) {
        return new RetryPolicy(
                seconds(settings, SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS, DEFAULT_BASE),
                seconds(settings, SettingKeys.EMAIL_RETRY_BACKOFF_MAX_SECONDS, DEFAULT_MAX));
    }

    /**
     * Delay after the given attempt number (1 for the first attempt).
     *
     * <p>Computed by repeated doubling rather than {@code base * 2^(n-1)}, so an attempt count
     * that somehow got large cannot overflow the multiplication into a negative duration — which
     * would produce a message due in the past, retried immediately, forever.
     */
    Duration delayAfterAttempt(int attemptNumber) {
        Duration delay = base;
        for (int i = 1; i < attemptNumber; i++) {
            if (delay.compareTo(max) >= 0) {
                return max;
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }

    private static Duration seconds(SettingsService settings, String key, Duration fallback) {
        try {
            int value = settings.requireInt(key);
            return value > 0 ? Duration.ofSeconds(value) : fallback;
        } catch (RuntimeException e) {
            // Same reasoning as EmailSenderImpl's attempt-count fallback: a missing retry
            // interval must not stop mail going out altogether. The dispatcher logs it.
            return fallback;
        }
    }
}
