package gr.novotrade.novocore.core.api.email;

/**
 * Whether a message body is plain text or HTML.
 *
 * <p>Stored on the message rather than sniffed from the content. Guessing by looking for angle
 * brackets gets a plain-text body containing {@code 3 < 5} wrong, and gets it wrong in the
 * direction that mangles what the recipient reads.
 */
public enum EmailBodyFormat {

    PLAIN_TEXT,

    HTML
}
