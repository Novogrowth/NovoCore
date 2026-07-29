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
 * <p>⚠️ <strong>{@code BundleService.allBundles()} and {@code bundlesWithUnpricedComponents()}
 * return unredacted {@code ProductView}s</strong>, unlike {@code ProductService}, which has a
 * {@code ...For} variant for every read. They are redacted here by calling
 * {@code ProductView.redactedFor} directly — the same single implementation the service variants
 * use, so the answer is identical — but the asymmetry is worth knowing about: the architecture rule
 * that stops a controller reaching an unredacted product read is written against
 * {@code ProductService} and does not cover these two. Giving {@code BundleService} its own
 * {@code For} variants would close that, and is a decision rather than an oversight to fix quietly.
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
        return ListResponse.of(redacted(bundles.allBundles()));
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
        return ListResponse.of(redacted(bundles.bundlesWithUnpricedComponents()));
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

    private List<ProductView> redacted(List<ProductView> products) {
        RoleView viewer = currentUser.require().role();
        return products.stream().map(product -> product.redactedFor(viewer)).toList();
    }

    /** The complete component list. A partial list would be a merge, which {@code define} is not. */
    record ComponentsRequest(List<NewBundleComponent> components) {
    }
}
