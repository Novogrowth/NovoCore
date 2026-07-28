package gr.novotrade.novocore.core.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.api.settings.SettingKeys;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final RetryPolicy SEEDED =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15));

    @Test
    @DisplayName("the delay doubles from the base")
    void doubles() {
        assertThat(SEEDED.delayAfterAttempt(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(SEEDED.delayAfterAttempt(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(SEEDED.delayAfterAttempt(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(SEEDED.delayAfterAttempt(4)).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    @DisplayName("the seeded settings spread five attempts over roughly eight minutes")
    void seededSettingsSpanAServerRestart() {
        // Recorded because it is the number that decides whether a mail server restart is
        // absorbed silently or produces a FAILED message somebody has to retry by hand.
        Duration total = Duration.ZERO;
        for (int attempt = 1; attempt <= 4; attempt++) {
            total = total.plus(SEEDED.delayAfterAttempt(attempt));
        }
        assertThat(total).isEqualTo(Duration.ofMinutes(7).plusSeconds(30));
    }

    @Test
    @DisplayName("the ceiling holds, and holds for every later attempt")
    void ceilingHolds() {
        assertThat(SEEDED.delayAfterAttempt(10)).isEqualTo(Duration.ofMinutes(15));
        assertThat(SEEDED.delayAfterAttempt(1000)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("a huge attempt count cannot overflow into a negative delay")
    void noOverflow() {
        // The reason the delay is computed by repeated doubling rather than base * 2^(n-1): an
        // overflowed multiplication produces a message due in the past, retried immediately,
        // forever — a loop with no error to notice.
        assertThat(SEEDED.delayAfterAttempt(Integer.MAX_VALUE))
                .isEqualTo(Duration.ofMinutes(15))
                .isPositive();
    }

    @Test
    @DisplayName("a zero or negative base is refused")
    void baseMustBePositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(Duration.ZERO, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(Duration.ofSeconds(-1), Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("a ceiling below the base is refused")
    void ceilingMustNotBeBelowTheBase() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(Duration.ofMinutes(5), Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("unreadable settings fall back rather than stopping mail altogether")
    void unreadableSettingsFallBack() {
        RetryPolicy policy = RetryPolicy.readFrom(new FakeSettingsService()
                .set(SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS, "half a minute"));

        assertThat(policy.base()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.max()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("configured values are used when they are usable")
    void configuredValuesWin() {
        RetryPolicy policy = RetryPolicy.readFrom(new FakeSettingsService()
                .set(SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS, "5")
                .set(SettingKeys.EMAIL_RETRY_BACKOFF_MAX_SECONDS, "20"));

        assertThat(policy.delayAfterAttempt(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfterAttempt(3)).isEqualTo(Duration.ofSeconds(20));
    }
}
