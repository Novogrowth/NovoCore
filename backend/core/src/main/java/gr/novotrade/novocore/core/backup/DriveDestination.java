package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One configured Google Drive account, read from {@code backup.drive.<key>.*}.
 *
 * <h2>OAuth refresh tokens, not a service account</h2>
 *
 * <p>A service account has no Drive storage quota of its own. Sharing a folder from an ordinary
 * Google account with one does not help: files it creates there are owned by the service account
 * and counted against its zero quota, so every upload fails with a storage error that reads like a
 * permissions problem. Shared Drives do solve it and require Google Workspace, which
 * {@code novotrade.gr} is not — its mail is self-hosted on {@code mail.novotrade.gr}. So each
 * destination carries its own OAuth client and refresh token, obtained once through the consent
 * flow for that account.
 *
 * <h2>All four values, or none</h2>
 *
 * <p>A destination with three of the four is not a destination configured slightly wrongly, it is
 * one that will fail every night. {@link #from} returns empty and the run records
 * {@code NOT_CONFIGURED} against it, naming what is missing, rather than attempting an upload that
 * cannot work.
 */
record DriveDestination(String key, String label, String folderId, String clientId,
        String clientSecret, String refreshToken) {

    /** The destinations this system knows about, in the order they are attempted. */
    static final List<String> KEYS = List.of("primary", "secondary");

    static String labelOf(SettingsService settings, String key) {
        return settings.find("backup.drive." + key + ".label")
                .filter(label -> !label.isBlank())
                .orElse(key);
    }

    /**
     * Reads one destination, or explains what is missing.
     *
     * @return the destination, or empty with the reason in {@code missing}
     */
    static Configured from(SettingsService settings, String key) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : List.of("folder-id", "client-id", "client-secret", "refresh-token")) {
            values.put(field, settings.find("backup.drive." + key + "." + field)
                    .map(String::trim)
                    .orElse(""));
        }

        List<String> missing = values.entrySet().stream()
                .filter(entry -> entry.getValue().isBlank())
                .map(entry -> "backup.drive." + key + "." + entry.getKey())
                .toList();

        String label = labelOf(settings, key);
        if (!missing.isEmpty()) {
            return new Configured(key, label, Optional.empty(),
                    "not configured: " + String.join(", ", missing) + " "
                            + (missing.size() == 1 ? "is" : "are") + " blank. Complete the OAuth "
                            + "consent flow for this account and supply the values.");
        }

        return new Configured(key, label, Optional.of(new DriveDestination(
                key, label,
                values.get("folder-id"),
                values.get("client-id"),
                values.get("client-secret"),
                values.get("refresh-token"))), null);
    }

    /** All destinations, configured or not — an unconfigured one still gets a row and a reason. */
    static List<Configured> all(SettingsService settings) {
        return KEYS.stream().map(key -> from(settings, key)).toList();
    }

    /**
     * @param destination present only when every required value is set
     * @param problem why not, when it is absent
     */
    record Configured(String key, String label, Optional<DriveDestination> destination,
            String problem) {

        boolean isConfigured() {
            return destination.isPresent();
        }
    }
}
