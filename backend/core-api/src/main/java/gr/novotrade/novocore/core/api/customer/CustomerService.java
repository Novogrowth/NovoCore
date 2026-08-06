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
 * and loses references. Recorded in {@code HISTORY.md} rather than stubbed.
 *
 * <p>There is no delete, for the reason stated across the core: history and journal lines.
 */
public interface CustomerService {

    /** Every customer, active and inactive, by name. */
    List<CustomerView> all();

    /** Active customers only. */
    List<CustomerView> active();

    /**
     * Customers whose name, VAT number, email or phone contains the term anywhere, ignoring case and
     * accents.
     *
     * <p><strong>This does not weaken {@link #findByVatNumber}, and must not be confused with
     * it.</strong> That lookup is exact because brief §5 makes the VAT number authoritative and lets
     * it auto-link a party without asking anybody; an exactness relaxed there would auto-link the
     * wrong company. This is a filter box, where a partial ΑΦΜ read off a document is exactly the
     * useful case and every result is chosen by a human looking at it.
     *
     * <p>The brief's field list also names <strong>Code</strong>, which is not a column yet — that
     * list is marked <em>(draft)</em> — so it is not searched. Queued as its own item.
     *
     * @param term matched as a substring; null or blank means no filter. Wildcards are literal.
     * @param activeOnly whether to restrict to active customers, combining with the term
     */
    List<CustomerView> search(String term, boolean activeOnly);

    Optional<CustomerView> find(long id);

    /** @throws CustomerNotFoundException if absent */
    CustomerView require(long id);

    /**
     * The customer carrying a system key — <strong>Q10's shared retail record</strong>.
     *
     * <p>Seeded by migration, the way the chart of accounts seeds its keyed accounts, and located by
     * key rather than by name for the same reason: the name is operator-editable and the id is an
     * implementation detail, so neither is a safe handle for code that must find one specific row.
     *
     * <p>It is <strong>not a default</strong>. Nothing falls back to it, and no sale is assigned to it
     * automatically. A till operator choosing "retail, no details" is stating a real answer to who
     * bought it, which is what makes it different from the catch-all step 5 refused to seed.
     *
     * @throws CustomerNotFoundException if no customer carries the key, which is a broken seed rather
     *     than a missing option
     */
    CustomerView require(CustomerSystemKey systemKey);

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

    /**
     * Deactivates a customer.
     *
     * @throws InvalidCustomerException if the customer carries a {@link CustomerSystemKey}. The shared
     *     retail record is structural: deactivating it would leave every till sale with nobody to be
     *     against. Refused here <em>and</em> by a CHECK constraint, so it holds against a {@code psql}
     *     session too.
     */
    void deactivate(long id);

    void reactivate(long id);
}
