package gr.novotrade.novocore.core.api.backup;

import java.util.List;
import java.util.Optional;

/**
 * Whether backups are able to run, and whether they are able to reach anywhere off-site.
 *
 * <p>For a Settings screen, and for answering "are we actually backed up?" honestly. The two
 * halves are reported separately on purpose: a system that dumps and encrypts correctly but has
 * no working destination is in a materially different state from one that is completely broken,
 * and from one that is fine — and the middle case is the one that looks fine.
 *
 * @param encryptionKeyFingerprint of the key currently configured, so it can be compared against
 *     the fingerprint recorded on an old artefact before anybody tries to restore it
 */
public record BackupConfigurationStatus(
        boolean canRun,
        String pgDumpVersion,
        String localDirectory,
        String encryptionKeyFingerprint,
        List<BackupDestinationStatus> destinations,
        String problem) {

    public BackupConfigurationStatus {
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
    }

    /** How many destinations could actually receive a backup right now. */
    public long usableDestinations() {
        return destinations.stream().filter(BackupDestinationStatus::isUsable).count();
    }

    /**
     * True when a backup taken now would end up somewhere other than this machine.
     *
     * <p>The honest headline. {@link #canRun()} being true and this being false is the state worth
     * shouting about: backups are running, they are correct, and every copy of them would be lost
     * along with the host they are protecting.
     */
    public boolean isOffsiteCapable() {
        return usableDestinations() > 0;
    }

    public Optional<String> problemIfAny() {
        return Optional.ofNullable(problem);
    }
}
