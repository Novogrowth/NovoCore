package gr.novotrade.novocore.core.api.settings;

/**
 * Setting keys used by the core. Constants rather than loose strings, so a typo is a compile
 * error and every reader of a setting can be found by its usages.
 *
 * <p>SMTP keys are declared here but deliberately have no seeded values — see the settings
 * migration. A placeholder credential is worse than a missing one, because it looks like
 * configuration.
 */
public final class SettingKeys {

    /**
     * Absolute residual at or below which a rounding difference posts automatically to the
     * Rounding account. Larger differences are flagged for review instead (brief §6).
     */
    public static final String LEDGER_ROUNDING_THRESHOLD = "ledger.rounding.threshold";

    /** {@link java.math.RoundingMode} name used wherever a monetary value must be rounded. */
    public static final String LEDGER_ROUNDING_MODE = "ledger.rounding.mode";

    /**
     * Cash payments at or above this amount are hard-blocked — the Greek legal cash limit
     * under N. 5301/2026, where penalties reach double the cash amount (brief §6).
     */
    public static final String CASH_PAYMENT_LIMIT = "cash.payment.limit";

    public static final String SMTP_HOST = "smtp.host";
    public static final String SMTP_PORT = "smtp.port";
    public static final String SMTP_USERNAME = "smtp.username";
    public static final String SMTP_PASSWORD = "smtp.password";
    public static final String SMTP_START_TLS = "smtp.start-tls";
    public static final String SMTP_FROM_ADDRESS = "smtp.from-address";
    public static final String SMTP_FROM_NAME = "smtp.from-name";

    private SettingKeys() {
    }
}
