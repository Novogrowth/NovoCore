package gr.novotrade.novocore.core.api.settings;

import gr.novotrade.novocore.core.api.email.EmailTransportSecurity;
import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What kind of value a catalogued setting holds, and therefore what counts as a valid one.
 *
 * <h2>Why a write has to parse before it stores</h2>
 *
 * <p>{@link SettingsService#put} takes any string. That is right for the store — it is a key/value
 * table and the typing lives in Java, where the readers are — and it is <strong>wrong for an API</strong>,
 * because the failure surfaces somewhere else entirely. Write {@code "0,03"} with a comma into
 * {@code ledger.rounding.threshold} today and the write succeeds; the failure arrives on the next
 * invoice somebody tries to record, as a {@code SettingValueException} naming a key they were not
 * thinking about, raised by a document that has nothing wrong with it.
 *
 * <p>So each entry in {@link SettingsCatalog} names its type, and the write path parses the value
 * with the same rule the reader will apply. A refused write leaves the old value in place, which is
 * the guarantee worth having: configuration that governs money never becomes unreadable because a
 * form was submitted with a typo.
 */
public enum SettingType {

    /** Free text, refused only when blank. */
    TEXT {
        @Override
        void check(String key, String value) {
            if (value.isBlank()) {
                throw new SettingValueException(key, "a non-blank value", value, null);
            }
        }
    },

    /** A whole number. */
    INTEGER {
        @Override
        void check(String key, String value) {
            try {
                Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new SettingValueException(key, "an integer", value, e);
            }
        }
    },

    /**
     * A positive whole number — for the several settings where zero or negative is meaningless.
     *
     * <p>Separate from {@link #INTEGER} because {@code email.max-attempts = 0} would queue mail that
     * is never attempted, and {@code email.dispatch.batch-size = 0} would run the dispatcher forever
     * claiming nothing. Both are accepted by a plain integer check and neither is a value anybody
     * means.
     */
    POSITIVE_INTEGER {
        @Override
        void check(String key, String value) {
            INTEGER.check(key, value);
            if (Integer.parseInt(value.trim()) < 1) {
                throw new SettingValueException(key, "a positive integer", value, null);
            }
        }
    },

    /** A EUR monetary amount — two decimals at most, since {@code Money} refuses more. */
    EUR_AMOUNT {
        @Override
        void check(String key, String value) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(value.trim());
            } catch (NumberFormatException e) {
                throw new SettingValueException(key, "a monetary amount", value, e);
            }
            try {
                Money.of(amount, Money.EUR);
            } catch (IllegalArgumentException e) {
                // Money refuses more than two decimals rather than rounding. Reported as a setting
                // problem so the message names the key instead of being a bare arithmetic complaint.
                throw new SettingValueException(key, "a monetary amount", value, e);
            }
        }
    },

    /** A {@link RoundingMode} name. */
    ROUNDING_MODE {
        @Override
        void check(String key, String value) {
            try {
                RoundingMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new SettingValueException(key, "a java.math.RoundingMode name", value, e);
            }
        }
    },

    /** An {@link EmailTransportSecurity} name — {@code NONE}, {@code STARTTLS} or {@code TLS}. */
    TRANSPORT_SECURITY {
        @Override
        void check(String key, String value) {
            try {
                EmailTransportSecurity.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new SettingValueException(
                        key, "an EmailTransportSecurity name "
                                + java.util.Arrays.toString(EmailTransportSecurity.values()),
                        value, e);
            }
        }
    },

    /**
     * A number of days, or the literal {@value SettingKeys#RETENTION_FOREVER}.
     *
     * <p>{@code FOREVER} is a word rather than a blank or a zero on purpose (Q43): the retention
     * prune has no safe default, and an unreadable value stops it and deletes nothing. That property
     * only holds if "keep everything" is something somebody can actually write down.
     */
    RETENTION_DAYS {
        @Override
        void check(String key, String value) {
            String supplied = value.trim();
            if (SettingKeys.RETENTION_FOREVER.equalsIgnoreCase(supplied)) {
                return;
            }
            try {
                if (Integer.parseInt(supplied) < 0) {
                    throw new SettingValueException(
                            key, "a number of days, or " + SettingKeys.RETENTION_FOREVER,
                            value, null);
                }
            } catch (NumberFormatException e) {
                throw new SettingValueException(
                        key, "a number of days, or " + SettingKeys.RETENTION_FOREVER, value, e);
            }
        }
    };

    /**
     * Refuses a value this type cannot hold.
     *
     * @throws SettingValueException naming the key, what was expected and what arrived
     */
    abstract void check(String key, String value);

    /** Package-private {@link #check} exposed for the catalogue, which is the only caller. */
    void validate(String key, String value) {
        check(key, value);
    }
}
