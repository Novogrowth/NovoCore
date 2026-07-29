package gr.novotrade.novocore.core.web.product;

import gr.novotrade.novocore.core.api.bundle.BundleComponentView;
import gr.novotrade.novocore.core.api.bundle.BundleService;
import gr.novotrade.novocore.core.api.bundle.NewBundleComponent;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bundles: a product with a component list, sellable as one thing.
 *
 * <p>Same section as products, because a bundle <em>is</em> a product — its own SKU, its own price
 * tag, its own invoice line — and the component list is an attribute of it rather than a separate
 * kind of record.
 *
 * <p>Both list reads use {@code BundleService}'s {@code ...For(viewer)} variants, for the same
 * reason the product routes use {@code ProductService}'s: a bundle <em>is</em> a
 * {@code ProductView}, so these lists carry the supplier, the supplier's SKU and the last purchase
 * price like any other.
 *
 * <p>Those variants were added in step 14c to close an asymmetry rather than a leak — this
 * controller previously called {@code ProductView.redactedFor} itself, which was correct and was a
 * convention held by hand in one place. The architecture rule forbidding the web layer from
 * reaching an unredacted product read was written against {@code ProductService} alone, so these
 * two sat outside it; now they are inside it, and the guarantee is structural rather than
 * remembered.
 */
@RestController
@Requires(section = Section.PRODUCTS)
class BundleController {

    private final BundleService bundles;
    private final CurrentUser currentUser;

    BundleController(BundleService bundles, CurrentUser currentUser) {
        this.bundles = bundles;
        this.currentUser = currentUser;
    }

    /** The component list of one bundle. Empty for a product that is not a bundle. */
    @GetMapping(path = "/api/products/{id}/components", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<BundleComponentView> components(@PathVariable long id) {
        return ListResponse.of(bundles.componentsOf(id));
    }

    /** The bundles one product is a component of — what a deactivation has to answer to. */
    @GetMapping(path = "/api/products/{id}/in-bundles", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<BundleComponentView> inBundles(@PathVariable long id) {
        return ListResponse.of(bundles.bundlesContaining(id));
    }

    @GetMapping(path = "/api/bundles", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<ProductView> allBundles() {
        return ListResponse.of(bundles.allBundlesFor(viewer()));
    }

    /**
     * Bundles that cannot be sold yet, because a component has no price.
     *
     * <p>An unpriced component makes decomposition refuse rather than weigh zero — weighing zero
     * would push all the revenue onto the priced components and report the unpriced one as pure
     * margin. This is the list that surfaces it <em>before</em> somebody tries to sell one.
     */
    @GetMapping(path = "/api/bundles/unpriced-components",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<ProductView> unpricedComponents() {
        return ListResponse.of(bundles.bundlesWithUnpricedComponentsFor(viewer()));
    }

    /**
     * Defines a bundle's components — <strong>replacing the whole list</strong>.
     *
     * <p>{@code PUT} rather than {@code PATCH} because {@code define} replaces and never merges, so
     * a bundle is never left half-changed and never exists empty: the flag and the components are
     * set in one transaction. A component may not itself be a bundle, so cycles are impossible by
     * construction rather than by a check.
     */
    @PutMapping(path = "/api/products/{id}/components",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    ListResponse<BundleComponentView> define(
            @PathVariable long id, @RequestBody ComponentsRequest request) {
        return ListResponse.of(bundles.define(id, request.components()));
    }

    /**
     * Dissolves a bundle back into an ordinary product.
     *
     * <p>The one {@code DELETE} in this slice, and it is honest: the component rows really are
     * removed. It does not strand sold bundles — a sale materialises its decomposition on the
     * invoice, so the history keeps its own component lines and does not depend on this list still
     * existing.
     */
    @DeleteMapping(path = "/api/products/{id}/components")
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dissolve(@PathVariable long id) {
        bundles.dissolve(id);
    }

    private RoleView viewer() {
        return currentUser.require().role();
    }

    /** The complete component list. A partial list would be a merge, which {@code define} is not. */
    record ComponentsRequest(List<NewBundleComponent> components) {
    }
}
