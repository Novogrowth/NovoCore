package gr.novotrade.novocore.core.api.inventory;

/** No such serialized unit. */
public class SerializedUnitNotFoundException extends RuntimeException {

    public SerializedUnitNotFoundException(long id) {
        super("No serialized unit with id " + id + ".");
    }

    private SerializedUnitNotFoundException(String message) {
        super(message);
    }

    public static SerializedUnitNotFoundException forSerialNumber(String serialNumber) {
        return new SerializedUnitNotFoundException(
                "No serialized unit with serial number '" + serialNumber + "'.");
    }
}
