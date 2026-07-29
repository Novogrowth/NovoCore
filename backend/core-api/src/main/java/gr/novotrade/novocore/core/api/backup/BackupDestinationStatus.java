package gr.novotrade.novocore.core.api.backup;

import java.util.Optional;

/**
 * Whether one configured destination is usable, established by actually talking to it.
 *
 * @param reachable true only if an access token was obtained and the target folder was found.
 *     A refresh token that reads back correctly and no longer works is the common failure —
 *     Google expires them on password change, on consent revocation, and after six months of
 *     disuse — and only a real call tells the two apart
 * @param folderName the folder's own name as Drive reports it. Present only when reachable, and
 *     worth surfacing: a folder id is unreadable, so "uploading into 'NovoCore Backups'" is the
 *     only practical confirmation that the configured id is the folder somebody meant
 * @param problem what is wrong, when something is
 */
public record BackupDestinationStatus(
        String destinationKey,
        String label,
        boolean configured,
        boolean reachable,
        String folderId,
        String folderName,
        String problem) {

    public static BackupDestinationStatus notConfigured(String key, String label, String problem) {
        return new BackupDestinationStatus(key, label, false, false, null, null, problem);
    }

    public static BackupDestinationStatus reachable(String key, String label, String folderId,
            String folderName) {
        return new BackupDestinationStatus(key, label, true, true, folderId, folderName, null);
    }

    public static BackupDestinationStatus unreachable(String key, String label, String folderId,
            String problem) {
        return new BackupDestinationStatus(key, label, true, false, folderId, null, problem);
    }

    public boolean isUsable() {
        return configured && reachable;
    }

    public Optional<String> problemIfAny() {
        return Optional.ofNullable(problem);
    }

    public Optional<String> folderNameIfAny() {
        return Optional.ofNullable(folderName);
    }
}
