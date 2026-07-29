package gr.novotrade.novocore.core.email;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reaches one attachment directly, by its own id.
 *
 * <p>Exists so that opening an attachment from the sent-email history is one lookup rather than
 * loading the whole message and searching its list — and so the id in
 * {@code SentEmailAttachmentView} is a handle that works on its own, for a referenced document
 * and an inline file alike.
 */
interface QueuedEmailAttachmentRepository extends JpaRepository<QueuedEmailAttachment, Long> {
}
