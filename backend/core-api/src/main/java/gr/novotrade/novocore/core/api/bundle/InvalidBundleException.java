package gr.novotrade.novocore.core.api.bundle;

/**
 * A bundle definition is not allowed — an empty component list, a bundle containing itself, a
 * component that is itself a bundle, a duplicated or inactive component, a quantity the component's
 * unit of measure cannot express, or a bundle product that has stock of its own.
 */
public class InvalidBundleException extends RuntimeException {

    public InvalidBundleException(String message) {
        super(message);
    }
}
