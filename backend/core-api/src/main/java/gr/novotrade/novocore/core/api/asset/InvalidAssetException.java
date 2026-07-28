package gr.novotrade.novocore.core.api.asset;

/**
 * A requested asset change is not allowed — a blank name, a duplicate asset code, a depreciation
 * rate that is not a percentage, a date before acquisition, or a disposal of an asset already
 * disposed of.
 */
public class InvalidAssetException extends RuntimeException {

    public InvalidAssetException(String message) {
        super(message);
    }
}
