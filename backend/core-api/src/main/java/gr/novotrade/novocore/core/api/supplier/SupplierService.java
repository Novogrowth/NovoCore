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
