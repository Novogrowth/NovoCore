package gr.novotrade.novocore.core.api.charge;

/** No such charge type. */
public class ChargeTypeNotFoundException extends RuntimeException {

    public ChargeTypeNotFoundException(long id) {
        super("No charge type with id " + id + ".");
    }
}
