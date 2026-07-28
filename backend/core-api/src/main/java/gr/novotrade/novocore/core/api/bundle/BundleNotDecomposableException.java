package gr.novotrade.novocore.core.api.bundle;

/**
 * Thrown when a bundle cannot be split into component lines.
 *
 * <p>Almost always because a component has no standalone selling price. Allocation is proportional to
 * standalone value, so an unpriced component has an <em>unknown</em> share rather than a zero one, and
 * the two are not interchangeable: treating it as zero would silently push all of the bundle's revenue
 * onto the priced components and report the unpriced one as pure margin.
 *
 * <p>This is the same stance {@code VatClassNotDeterminableException} takes on a missing VAT class,
 * and for the same reason — there is no defensible default, so it throws rather than assuming one.
 * {@code BundleService.bundlesWithUnpricedComponents()} exists so this is found before a sale rather
 * than during one.
 */
public class BundleNotDecomposableException extends RuntimeException {

    public BundleNotDecomposableException(String message) {
        super(message);
    }

    public static BundleNotDecomposableException unpricedComponent(
            long bundleProductId, String componentSku) {
        return new BundleNotDecomposableException(
                "Bundle " + bundleProductId + " cannot be decomposed: component '" + componentSku
                        + "' has no selling price, so its share of the bundle's value is unknown. "
                        + "Treating it as zero would report the whole bundle's revenue against the "
                        + "other components and show this one as pure margin. Price it, or remove it "
                        + "from the bundle.");
    }

    public static BundleNotDecomposableException noComponents(long bundleProductId) {
        return new BundleNotDecomposableException(
                "Bundle " + bundleProductId + " has no components, so there is nothing to decompose "
                        + "it into.");
    }
}
