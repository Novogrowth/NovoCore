package gr.novotrade.novocore.core.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM over a backup artefact, streaming, with the checksum computed on the way past.
 *
 * <p>GCM rather than CBC: a backup is written once and read years later, quite possibly after
 * being copied between two Google accounts and back. Confidentiality alone would let a truncated
 * or altered file decrypt into plausible-looking garbage that {@code pg_restore} then fails on
 * for an unrelated-sounding reason. GCM authenticates, so a damaged artefact is reported as
 * damaged.
 *
 * <h2>Two traps, both deliberately avoided</h2>
 *
 * <p><strong>{@code CipherInputStream} is not used, and must not be introduced later.</strong> It
 * swallows {@code AEADBadTagException} from {@code doFinal} on close and simply reports
 * end-of-stream — so a tampered or truncated backup decrypts to a short plaintext with no error
 * at all, which discards the entire reason for choosing an authenticated cipher. The read path
 * here loops over {@code update} and calls {@code doFinal} itself so that failure is thrown.
 *
 * <p><strong>The IV is random per artefact and never reused.</strong> Reusing an IV under one key
 * is the failure that breaks GCM outright, not merely weakens it. A fresh 96-bit value from
 * {@link SecureRandom} per file is the standard construction; the counter-based alternative would
 * need durable state, and durable state for backups is exactly what cannot be relied on.
 *
 * <h2>The plaintext dump never reaches disk</h2>
 *
 * <p>{@code pg_dump}'s stdout is piped straight through {@link #encrypt} into the artefact file.
 * There is no intermediate unencrypted file to be forgotten in a temp directory, or to be read by
 * anything else on the host in the seconds it would exist.
 */
final class BackupCipher {

    /**
     * File header. Identifies the format and pins the version, so a future change of algorithm is
     * detectable rather than presenting as corruption.
     */
    static final byte[] MAGIC = "NOVOBK01".getBytes(StandardCharsets.US_ASCII);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int BUFFER_BYTES = 64 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCipher() {
    }

    /**
     * Encrypts {@code plaintext} into {@code target}, returning what was written.
     *
     * <p>The checksum is over the <em>encrypted</em> bytes — the artefact as it actually exists,
     * so a copy fetched back from Drive can be verified without the key and therefore by anyone
     * checking that the upload arrived intact.
     */
    static Written encrypt(InputStream plaintext, OutputStream target, SecretKeySpec key)
            throws IOException, GeneralSecurityException {

        byte[] iv = new byte[IV_LENGTH_BYTES];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        // The header is authenticated but not encrypted, so a file whose magic has been altered
        // fails the tag check rather than being read as a different format.
        cipher.updateAAD(MAGIC);

        MessageDigest digest = sha256();
        // Not closed, deliberately, and an IDE will say so: it wraps a stream the caller owns and
        // holds no resource of its own. Closing here would close the caller's file underneath it,
        // which is the opposite of a leak and much harder to notice.
        CountingDigestOutputStream out = new CountingDigestOutputStream(target, digest);

        out.write(MAGIC);
        out.write(iv);

        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = plaintext.read(buffer)) != -1) {
            byte[] encrypted = cipher.update(buffer, 0, read);
            if (encrypted != null) {
                out.write(encrypted);
            }
        }
        out.write(cipher.doFinal());
        out.flush();

        return new Written(out.count(), HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * Decrypts {@code artefact} into {@code target}.
     *
     * @throws GeneralSecurityException if the artefact was encrypted with a different key, or has
     *     been altered or truncated. These are indistinguishable by design — that is what an
     *     authentication tag is — which is why {@code backup_run.encryption_key_fingerprint} is
     *     recorded separately and checked before this is ever called.
     * @throws IOException if the header is not this format at all
     */
    static void decrypt(InputStream artefact, OutputStream target, SecretKeySpec key)
            throws IOException, GeneralSecurityException {

        byte[] magic = artefact.readNBytes(MAGIC.length);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException(
                    "This file is not a NovoCore backup artefact: expected the header %s, found %s."
                            .formatted(new String(MAGIC, StandardCharsets.US_ASCII),
                                    HexFormat.of().formatHex(magic)));
        }

        byte[] iv = artefact.readNBytes(IV_LENGTH_BYTES);
        if (iv.length != IV_LENGTH_BYTES) {
            throw new IOException("Backup artefact is truncated: it ends inside its header.");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(MAGIC);

        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = artefact.read(buffer)) != -1) {
            byte[] plain = cipher.update(buffer, 0, read);
            if (plain != null) {
                target.write(plain);
            }
        }
        // The line CipherInputStream would have swallowed. Everything above this point is
        // unauthenticated plaintext; only this call establishes that it was genuine.
        target.write(cipher.doFinal());
        target.flush();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }

    /** @param checksumSha256 lower-case hex, over the encrypted artefact */
    record Written(long sizeBytes, String checksumSha256) {
    }

    /**
     * Counts and digests on the way through, so the artefact is never re-read to measure it. A
     * second pass would double the I/O on the largest file this system produces, and would leave
     * open the possibility of measuring something other than what was written.
     */
    private static final class CountingDigestOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final MessageDigest digest;
        private long count;

        CountingDigestOutputStream(OutputStream delegate, MessageDigest digest) {
            this.delegate = delegate;
            this.digest = digest;
        }

        long count() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            digest.update((byte) b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            digest.update(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
    }
}
