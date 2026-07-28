package gr.novotrade.novocore.core.api.email;

/**
 * Thrown when the dispatcher is asked to send and the SMTP settings are incomplete.
 *
 * <p>Note where this is <em>not</em> thrown: {@code EmailSender.send} still queues the message.
 * Refusing to queue would mean an operation that legitimately succeeded — a Purchase Order was
 * approved — failing because of a mail setting, and the notification would then be lost rather
 * than delayed. The message waits in the outbox and goes out when the configuration is fixed.
 */
public class EmailNotConfiguredException extends RuntimeException {

    public EmailNotConfiguredException(String message) {
        super(message);
    }

    public EmailNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
