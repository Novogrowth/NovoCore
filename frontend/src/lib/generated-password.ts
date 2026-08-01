/**
 * The password an administrator hands to somebody else.
 *
 * **Generated, never typed.** `PATCH /api/users/{id}/password` is an administrator setting an
 * account they do not own — a reset, an offboarding, or a first login — and a password a person
 * invents on somebody else's behalf is, in practice, one they will remember, which means one
 * somebody else can guess. The operator's job here is to carry a value from one screen to one
 * person, not to think of one.
 *
 * **The policy this satisfies is the backend's, and it is length only**: `PasswordPolicy` requires
 * twelve characters and no mixture of character classes, following NIST SP 800-63B, which dropped
 * composition rules because they push people towards predictable substitutions. So this is longer
 * than the minimum rather than more decorated than it.
 */

/**
 * Twenty, against a minimum of twelve.
 *
 * Longer costs nothing — it is copied, not typed from memory — and the value is protected only
 * by its own entropy between being shown and being changed by whoever receives it.
 */
const LENGTH = 20

/**
 * No `0`/`O`, no `1`/`l`/`I`, no punctuation.
 *
 * The one thing this value has to survive is being read off a screen and typed, or dictated over a
 * phone, exactly once. Ambiguous glyphs and symbols that differ per keyboard layout — this is a
 * Greek office — are what makes that fail, and a failed hand-off is another reset.
 */
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'

/**
 * A password from the platform's cryptographic random source.
 *
 * ⚠️ **`Math.random()` would be wrong here and would look identical.** It is not a CSPRNG and its
 * output is predictable from previous values; nothing in the UI would ever show the difference.
 * `crypto.getRandomValues` is available in every browser this application supports, and is the
 * reason this is a two-line function rather than a dependency.
 *
 * The bytes are rejection-sampled rather than taken modulo the alphabet's length: 256 is not a
 * multiple of 57, so `byte % 57` would make the first 28 characters of the alphabet very slightly
 * likelier than the rest. The bias is small and the fix is free.
 */
export function generatePassword(): string {
  const limit = Math.floor(256 / ALPHABET.length) * ALPHABET.length
  let password = ''

  while (password.length < LENGTH) {
    const bytes = new Uint8Array(LENGTH)
    crypto.getRandomValues(bytes)
    for (const byte of bytes) {
      if (byte >= limit) continue
      password += ALPHABET[byte % ALPHABET.length]
      if (password.length === LENGTH) break
    }
  }

  return password
}
