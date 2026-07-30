package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.security.InvalidUserException;
import java.util.regex.Pattern;

/**
 * What a user's stored language preference must look like.
 *
 * <p>The sibling of {@link PasswordPolicy}: a small rule about one user field, stated once, so the
 * service and the database agree about it rather than each having an opinion.
 *
 * <p><strong>The shape is checked; the set of languages is not.</strong> Q47(b) settled that the
 * backend localises nothing — every message it produces stays English — so it has no basis for a
 * list of supported languages, and inventing one here would be claiming support that does not
 * exist. Which languages are offered is the frontend's decision; this only refuses values that are
 * not language tags at all.
 *
 * <p>The trigger to revisit is named in V27 and worth repeating: the day the backend localises its
 * own messages, the supported set becomes code — an enum, as {@code Section} is — and both this and
 * the CHECK constraint gain the list.
 *
 * <p>Normalisation is deliberate and mirrors {@code username}: {@code el-gr}, {@code EL-GR} and
 * {@code el-GR} are one preference, and storing three spellings of it would make the column
 * unusable for anything that later groups by it.
 */
final class LanguageTag {

    /**
     * A lowercase ISO 639 primary subtag, optionally an uppercase ISO 3166 region.
     *
     * <p>Applied <em>after</em> normalisation, so it is the same expression the database CHECK
     * uses. Two statements of one rule that cannot disagree, which is the arrangement
     * {@code journal_source_is_amendable} settled on.
     */
    private static final Pattern SHAPE = Pattern.compile("^[a-z]{2,3}(-[A-Z]{2})?$");

    private LanguageTag() {
    }

    /**
     * Normalises and checks a language tag.
     *
     * @param raw the tag as supplied, or null/blank to clear the preference
     * @return the normalised tag, or null meaning "this person has not chosen" — which is a real
     *     answer and not a missing one, so clearing it is allowed rather than refused
     * @throws InvalidUserException if the value is not a language tag. The message names the shape
     *     and gives an example, because a caller who cannot see what was wrong cannot fix it.
     */
    static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        int dash = trimmed.indexOf('-');
        String normalised = dash < 0
                ? trimmed.toLowerCase(java.util.Locale.ROOT)
                : trimmed.substring(0, dash).toLowerCase(java.util.Locale.ROOT)
                        + "-" + trimmed.substring(dash + 1).toUpperCase(java.util.Locale.ROOT);

        if (!SHAPE.matcher(normalised).matches()) {
            throw new InvalidUserException(
                    "'" + trimmed + "' is not a language tag. Expected a two- or three-letter "
                            + "language code, optionally with a region — for example \"el\", "
                            + "\"en\" or \"el-GR\". Send null or an empty value to clear the "
                            + "preference.");
        }
        return normalised;
    }
}
