package gr.novotrade.novocore.core.api.supplier;

import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.List;
import java.util.Optional;

/**
 * Suppliers: the sub-ledger behind Accounts payable and the GR/IR clearing account.
 *
 * <p><strong>There is no delete</strong>, here or anywhere else in the core. A supplier that has
 * been posted to cannot be removed without either destroying history or leaving journal lines
 * pointing at nothing, and with no period locking there is no point at which one is safely
 * finished with. {@link #deactivate} takes them out of circulation instead.
 *
 * <p><strong>Matching is split by certainty</strong>, per {@code CLAUDE.md} rule 7.
 * {@link #findByVatNumber} is an exact match on a strong identifier and may be applied
 * automatically; {@link #suggestMatches} returns candidates that a human confirms. Nothing here
 * merges two suppliers — see {@code CustomerService} for why that is deliberately absent from
 * this step.
 */
public interface SupplierService {

    /** Every supplier, active and inactive, by name. */
    List<SupplierView> all();

    /** Active suppliers only — what a "choose a supplier" picker should offer. */
    List<SupplierView> active();

    /**
     * Suppliers whose name, VAT number, email or phone contains the term anywhere, ignoring case
     * and accents.
     *
     * <p><strong>Not the same thing as {@link #suggestMatches}, and the difference is what the
     * answer is <em>for</em>.</strong> This is an operator looking through a list they already know
     * they want to be in — a filter box, where more results is a mild inconvenience. Match
     * suggestions feed the never-silently-guess flow of {@code CLAUDE.md} rule 7, where each
     * candidate is offered to a human as a possible identity for a party on an incoming document. A
     * loose match is cheap in the first and expensive in the second, so they stay separate rather
     * than one being expressed as the other.
     *
     * <p>The VAT number is searched as a substring, and that does <strong>not</strong> weaken
     * {@link #findByVatNumber}: that lookup stays exact because it is brief §5's authoritative
     * auto-link, and the reason it may be applied without asking anybody is that it cannot match
     * approximately.
     *
     * <p>The brief's field list also names <strong>Code</strong> and <strong>Alias</strong>. Neither
     * is a column yet — that list is marked <em>(draft)</em> and step 5 built neither — so neither is
     * searched. They are queued as their own item, since a code nobody can enter would make this
     * method's contract a claim about data that cannot exist.
     *
     * @param term matched as a substring; null or blank means no filter, so the whole list comes
     *     back rather than nothing. Wildcards are matched literally.
     * @param activeOnly whether to restrict to active suppliers, combining with the term rather than
     *     replacing it
     */
    List<SupplierView> search(String term, boolean activeOnly);

    Optional<SupplierView> find(long id);

    /** @throws SupplierNotFoundException if absent */
    SupplierView require(long id);

    /**
     * Exact match on the VAT number, which brief §5 treats as authoritative.
     *
     * <p>Safe to apply without confirmation: a VAT number is a strong identifier issued by an
     * authority, and the schema refuses two suppliers sharing one.
     */
    Optional<SupplierView> findByVatNumber(String vatNumber);

    /**
     * Candidate matches on a name, email or phone number, for a human to confirm.
     *
     * <p><strong>Suggestions, never a decision.</strong> A shared email address or an office
     * phone number is evidence, not proof, and auto-merging on it would silently attribute one
     * company's purchases to another. Returns everything plausible and picks nothing — the
     * one-click confirmation is the caller's to offer.
     *
     * <p>Phone numbers are compared as stored. Nothing normalises {@code +30}, {@code 0030} and a
     * bare local number into one form yet, so a match on a differently-formatted number will not
     * be found; that normalisation belongs with the adapters that import contact data.
     */
    List<SupplierView> suggestMatches(String nameFragment, String email, String phone);

    /**
     * Adds a supplier.
     *
     * @throws InvalidSupplierException if the name is blank, the name or VAT number duplicates an
     *     existing supplier, the VAT status requires a VAT number or an exemption reason that is
     *     absent, or the exemption reason does not exist
     */
    SupplierView create(NewSupplier request);

    /** @throws InvalidSupplierException if the new name duplicates another supplier */
    SupplierView rename(long id, String newName);

    /** Updates the contact details. Either may be null, meaning "not known". */
    SupplierView changeContactDetails(long id, String email, String phone);

    /**
     * Records or clears the VAT number.
     *
     * @throws InvalidSupplierException if another supplier already has it, or if clearing it would
     *     leave a status that requires one without one
     */
    SupplierView changeVatNumber(long id, String vatNumber);

    /**
     * Reclassifies the supplier for VAT.
     *
     * @throws InvalidSupplierException if the new status requires a VAT number or an exemption
     *     reason the supplier does not have
     */
    SupplierView changeVatStatus(long id, VatStatus vatStatus, Long vatExemptionReasonId);

    void deactivate(long id);

    void reactivate(long id);
}
