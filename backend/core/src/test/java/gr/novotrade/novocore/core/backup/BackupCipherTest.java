package gr.novotrade.novocore.core.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The encryption a backup artefact carries.
 *
 * <p>The round trip is the easy half and is asserted first. The half that matters is what happens
 * to a <em>damaged</em> artefact: authenticated encryption is only worth choosing if a corrupted
 * or truncated file is reported as such rather than decrypting into plausible garbage, and the
 * standard way to lose that guarantee — {@code CipherInputStream} swallowing the tag check — fails
 * silently and would pass a naive round-trip test.
 */
class BackupCipherTest {

    private static final byte[] DUMP =
            ("PGDMP fake custom-format dump, long enough to span more than one cipher update "
                    + "block so that streaming is genuinely exercised. ").repeat(400)
                    .getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key = randomKey();

    @Test
    @DisplayName("what goes in comes back out")
    void roundTrip() throws Exception {
        byte[] artefact = encrypt(DUMP, key);
        assertThat(decrypt(artefact, key)).isEqualTo(DUMP);
    }

    @Test
    @DisplayName("the artefact does not contain the plaintext")
    void plaintextIsNotRecognisable() throws Exception {
        byte[] artefact = encrypt(DUMP, key);

        // The obvious property, worth an explicit test because the failure mode of getting the
        // cipher wiring wrong is often a file that is merely wrapped rather than encrypted.
        String asText = new String(artefact, StandardCharsets.ISO_8859_1);
        assertThat(asText).doesNotContain("PGDMP fake custom-format dump");
        assertThat(artefact.length)
                .as("ciphertext plus the header and the GCM tag")
                .isGreaterThan(DUMP.length);
    }

    @Test
    @DisplayName("the size and checksum reported are of the artefact as written")
    void reportedSizeAndChecksumDescribeTheEncryptedFile() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        BackupCipher.Written written =
                BackupCipher.encrypt(new ByteArrayInputStream(DUMP), target, key);

        // The checksum is over the encrypted bytes on purpose: a copy fetched back from Drive can
        // then be verified by anyone, without the key.
        assertThat(written.sizeBytes()).isEqualTo(target.toByteArray().length);
        assertThat(written.checksumSha256()).isEqualTo(sha256Hex(target.toByteArray()));
        assertThat(written.checksumSha256()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("every artefact gets its own IV, so two identical dumps differ")
    void ivIsNeverReused() throws Exception {
        // Reusing an IV under one key breaks GCM outright rather than weakening it. Two
        // encryptions of identical input must not produce identical output.
        byte[] first = encrypt(DUMP, key);
        byte[] second = encrypt(DUMP, key);

        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOfRange(first, 8, 20))
                .as("the 12-byte IV that follows the magic")
                .isNotEqualTo(Arrays.copyOfRange(second, 8, 20));
    }

    // -------------------------------------------------------------------------------------
    // The half that matters
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("a tampered artefact is refused, not silently truncated")
    void tamperingIsDetected() throws Exception {
        byte[] artefact = encrypt(DUMP, key);
        // Flip one bit deep inside the ciphertext, past the header.
        artefact[artefact.length / 2] ^= 0x01;

        assertThatExceptionOfType(AEADBadTagException.class)
                .isThrownBy(() -> decrypt(artefact, key));
    }

    @Test
    @DisplayName("a truncated artefact is refused rather than restoring a partial database")
    void truncationIsDetected() throws Exception {
        // The failure this test exists for. With CipherInputStream, this case decrypts to a
        // shorter plaintext and reports success — which would hand pg_restore a partial dump and
        // turn "half your backup is missing" into an unrelated-looking restore error much later.
        byte[] artefact = encrypt(DUMP, key);
        byte[] truncated = Arrays.copyOf(artefact, artefact.length - 64);

        assertThat(catchThrowable(() -> decrypt(truncated, key)))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("the wrong key is refused")
    void wrongKeyIsRefused() throws Exception {
        byte[] artefact = encrypt(DUMP, key);

        assertThatExceptionOfType(AEADBadTagException.class)
                .isThrownBy(() -> decrypt(artefact, randomKey()));
    }

    @Test
    @DisplayName("an altered header is refused, because it is authenticated too")
    void headerIsAuthenticated() throws Exception {
        byte[] artefact = encrypt(DUMP, key);
        artefact[0] = 'X';

        // Caught by the magic check first, which is the friendlier of the two failures — but the
        // header is fed in as AAD as well, so a header that survived the check and had been
        // altered would still fail the tag.
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> decrypt(artefact, key))
                .withMessageContaining("not a NovoCore backup artefact");
    }

    @Test
    @DisplayName("a file that is not an artefact at all says so plainly")
    void foreignFileIsRejectedClearly() {
        byte[] notAnArtefact = "this is a text file".getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> decrypt(notAnArtefact, key))
                .withMessageContaining("NOVOBK01");
    }

    @Test
    @DisplayName("an empty artefact is refused rather than restoring nothing")
    void emptyArtefactIsRefused() {
        assertThat(catchThrowable(() -> decrypt(new byte[0], key)))
                .isInstanceOf(IOException.class);
    }

    // -------------------------------------------------------------------------------------

    private static byte[] encrypt(byte[] plaintext, SecretKeySpec key) throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        BackupCipher.encrypt(new ByteArrayInputStream(plaintext), target, key);
        return target.toByteArray();
    }

    private static byte[] decrypt(byte[] artefact, SecretKeySpec key) throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        BackupCipher.decrypt(new ByteArrayInputStream(artefact), target, key);
        return target.toByteArray();
    }

    private static SecretKeySpec randomKey() {
        byte[] bytes = new byte[BackupEncryptionKey.KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "AES");
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(content));
    }
}
