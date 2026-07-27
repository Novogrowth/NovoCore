package gr.novotrade.novocore.core.api.charge;

import java.util.List;
import java.util.Optional;

/**
 * Chargeable fee types — COD charges, delivery costs, and whatever is added later.
 *
 * <p><strong>Not usable on a real invoice yet.</strong> Sales Invoice line items do not exist
 * (step 9), so nothing consumes these. The entity is built now because it depends on VAT classes
 * and the chart of accounts, both of which do exist, and because the alternative — hardcoding a
 * COD fee and a delivery fee at the point invoice lines are built — is exactly the shape that
 * later needs unpicking.
 */
public interface ChargeTypeService {

    /** Every charge type, active and inactive, by name. */
    List<ChargeTypeView> all();

    /** Active types only — what an "add a fee line" picker should offer. */
    List<ChargeTypeView> active();

    Optional<ChargeTypeView> find(long id);

    /** @throws ChargeTypeNotFoundException if absent */
    ChargeTypeView require(long id);

    Optional<ChargeTypeView> findByName(String name);

    /**
     * Adds a charge type.
     *
     * <p>The income account is checked to be an {@code INCOME}-type account. That check is the
     * point of this method existing rather than a bare repository save: a delivery fee wired to
     * net against the {@code Transportation costs} expense account would silently understate both
     * revenue and cost, and the resulting gross margin would look plausible while being wrong.
     * {@code CONTRA_INCOME} is refused too — that side is for sales returns.
     *
     * @throws InvalidChargeTypeException if the name duplicates an existing type, the VAT class
     *     does not exist or is inactive, or the account is not an active income account
     */
    ChargeTypeView create(NewChargeType request);

    /** @throws InvalidChargeTypeException if the name duplicates another type */
    ChargeTypeView rename(long id, String newName);

    /**
     * Changes the VAT class a fee line defaults to.
     *
     * @throws InvalidChargeTypeException if the VAT class does not exist or is inactive
     */
    ChargeTypeView changeDefaultVatClass(long id, long vatClassId);

    /**
     * Changes the income account a fee posts to.
     *
     * @throws InvalidChargeTypeException if the account is not an active income account
     */
    ChargeTypeView changeIncomeAccount(long id, long incomeAccountId);

    void deactivate(long id);

    void reactivate(long id);
}
