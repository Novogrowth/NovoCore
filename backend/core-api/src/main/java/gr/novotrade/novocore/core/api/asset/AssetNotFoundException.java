package gr.novotrade.novocore.core.api.asset;

/** No such fixed asset. */
public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException(long id) {
        super("No asset with id " + id + ".");
    }
}
