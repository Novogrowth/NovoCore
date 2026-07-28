package gr.novotrade.novocore.core.api.customer;

import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.List;
import java.util.Optional;

/**
 * Customers: the sub-ledger behind Accounts receivable.
 *
 * <p><strong>Matching is split by how certain it is</strong> — {@code CLAUDE.md} rule 7 and brief
 * §5's identity model. {@link #findByVatNumber} is an exact match on an authority-issued
 * identifier and may be applied automatically. {@link #suggestMatches} returns candidates on
 * email, phone or name, which are evidence rather than proof and require confirmation. The split
 * is in the method names because it is the whole rule: two people at one household email address
 * are two customers, and merging them silently would attribute one person's purchases and credit
 * balance to another.
 *
 * <p><strong>Merging is deliberately absent from this step.</strong> Brief §5 specifies that a
 * merge aliases the old id forward and never rewrites history, which needs an alias table and a
 * decision about what happens to postings already made under the retired id. Neither exists until
 * the ledger does (step 7), and building half of it now would mean a merge that appears to work
 * and loses references. Recorded in {@code PROGRESS.md} rather than stubbed.
 *
 * <p>There is no delete, for the reason stated across the core: history and journal lines.
 */
public interface CustomerService {

    /** Every customer, active and inactive, by name. */
    List<CustomerView> all();

    /** Active customers only. */
    List<CustomerView> active();

    Optional<CustomerView> find(long id);

    /** @throws CustomerNotFoundException if absent */
    CustomerView require(long id);

    /**
     * Exact match on the VAT number — brief §5's authoritative identifier.
     *
     * <p>Safe to apply without confirmation, and the only lookup here that is. The schema refuses
     * two customers sharing a VAT number, so this cannot return an arbitrary one of several.
     */
    Optional<CustomerView> findByVatNumber(String vatNumber);

    /**
     * Candidate matches on name, email or phone — <strong>suggestions, never a decision</strong>.
     *
     * <p>Brief §5 calls email and phone suggestive-only for a concrete reason: a shared household
     * address, a company switchboard number, or a courier's phone number entered on a delivery all
     * produce false matches. This returns everything plausible and picks nothing; offering the
     * one-click confirmation is the caller's job.
     *
     * <p>Phone numbers are compared as stored, so {@code +30 210 1234567} and {@code 2101234567}
     * will not match each other. Normalising them belongs with the adapters that import contact
     * data, where the source format is known.
     */
    List<CustomerView> suggestMatches(String nameFragment, String email, String phone);

    /**
     * Adds a customer.
     *
     * @throws InvalidCustomerException if the name is blank, the VAT number duplicates an existing
     *     customer, the VAT status requires a VAT number or an exemption reason that is absent, or
     *     a referenced VAT class or exemption reason does not exist
     */
    CustomerView create(NewCustomer request);

    /**
     * Renames a customer.
     *
     * <p>Duplicate names are permitted, unlike duplicate VAT numbers. Two unrelated retail
     * customers genuinely can be called "Γιώργος Παπαδόπουλος", and refusing the second one would
     * push whoever is serving them into inventing a distinguishing suffix.
     */
    CustomerView rename(long id, String newName);

    /** Updates contact details. Either may be null, meaning "not known". */
    CustomerView changeContactDetails(long id, String email, String phone);

    /**
     * Records or clears the VAT number.
     *
     * @throws InvalidCustomerException if another customer already has it, or if clearing it would
     *     leave a status that requires one without one
     */
    CustomerView changeVatNumber(long id, String vatNumber);

    /**
     * Reclassifies the customer for VAT.
     *
     * @throws InvalidCustomerException if the new status requires a VAT number or an exemption
     *     reason the customer does not have, or the exemption reason does not exist
     */
    CustomerView changeVatStatus(long id, VatStatus vatStatus, Long vatExemptionReasonId);

    /**
     * Sets or clears the customer's VAT class override — the middle level of the precedence rule.
     *
     * @throws InvalidCustomerException if the VAT class does not exist or is inactive
     */
    CustomerView changeVatClassOverride(long id, Long vatClassId);

    void deactivate(long id);

    void reactivate(long id);
}
