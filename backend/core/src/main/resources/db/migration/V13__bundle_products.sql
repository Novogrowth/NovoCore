-- Step 6, second half: bundle / composite products.
--
-- ---------------------------------------------------------------------------------------
-- Q11, ANSWERED: BUILT NOW, TO BRIEF §5 IN FULL
-- ---------------------------------------------------------------------------------------
-- We currently sell bundled products, so this is not speculative scope. V9 deliberately left out even
-- a flag, on the grounds that a flag nothing honours reads as a half-built feature; the answer is to
-- build the whole thing rather than the flag.
--
-- Brief §5 asks for five things, and all five are in this step:
--   1. own SKU                      — a bundle IS a Product, so a scan, a price tag, a Woo listing
--                                     and an invoice line can all treat it as one sellable item;
--   2. no stock of its own          — refused a lot by CHECK, and its availability is computed from
--                                     its components (see the ProductType note below);
--   3. proportional allocation      — BundleAllocation, exact integer arithmetic in cents;
--   4. decomposition into lines     — BundleService.decompose, ONE core-level rule replacing the
--                                     three ad hoc implementations across Woo, Skroutz and Go;
--   5. dual-level revenue reporting — the report is phase 8; what this step owes it is the LINK
--                                     between the levels, which BundleDecomposition carries and
--                                     enforces by construction (component lines sum to the bundle
--                                     line, exactly, so a report can roll up either way and adding
--                                     them together is visibly double-counting).
--
-- ---------------------------------------------------------------------------------------
-- ONE LEVEL DEEP, DELIBERATELY
-- ---------------------------------------------------------------------------------------
-- A component may not itself be a bundle. Same rule and same reasoning as V5's island-reduced VAT
-- counterpart: it makes a cycle impossible by construction rather than by a recursive check that has
-- to be got right, and it keeps allocation single-pass — a nested bundle would need its own discount
-- split before it could take a share of its parent's, and nothing in the brief asks for that.
--
-- Enforced in the service, because a CHECK cannot read the other row's `bundle` flag. The
-- self-reference half IS a CHECK, since that one is on the same row.

ALTER TABLE product
    ADD COLUMN bundle boolean NOT NULL DEFAULT false;

-- A bundle has no stock of its own, so there is nothing to identify individually. Also the reverse
-- direction of the same fact: serial numbers belong to things that arrive, and a bundle is assembled.
ALTER TABLE product
    ADD CONSTRAINT product_bundle_is_not_serial_tracked CHECK (
        bundle = false OR serial_tracked = false);

COMMENT ON COLUMN product.bundle IS
    'Whether this product is a bundle/composite (Q11, brief §5). Has its own SKU and NO stock of its '
    'own: availability is computed from components, and inventory_lot refuses one. A bundle always has '
    'at least one component, because BundleService.define sets the flag and the components in one '
    'transaction.';

-- ---------------------------------------------------------------------------------------
-- Components
-- ---------------------------------------------------------------------------------------

CREATE TABLE bundle_component (
    id                     bigserial     PRIMARY KEY,

    bundle_product_id      bigint        NOT NULL,
    component_product_id   bigint        NOT NULL,

    -- How many of the component per ONE bundle. numeric(19,6) like every other quantity, because a
    -- gift set containing 0.250 kg of coffee is an ordinary bundle. Whether a fraction is meaningful
    -- is the component unit's own answer (unit_of_measure.fractional_quantity_allowed), which the
    -- service reads rather than keeping a list of its own — the V11 obligation, discharged.
    quantity_per_bundle    numeric(19,6) NOT NULL,

    created_at             timestamptz   NOT NULL DEFAULT now(),
    created_by             varchar(100)  NOT NULL DEFAULT 'system',
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    updated_by             varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT bundle_component_bundle_fk
        FOREIGN KEY (bundle_product_id) REFERENCES product (id),
    CONSTRAINT bundle_component_component_fk
        FOREIGN KEY (component_product_id) REFERENCES product (id),

    -- One row per component. Two rows for the same component would be two quantities to add up, and
    -- the natural mistake is then editing one of them and not the other.
    CONSTRAINT bundle_component_unique UNIQUE (bundle_product_id, component_product_id),

    -- The self-reference half of "one level deep". A bundle containing itself is an infinite bundle.
    CONSTRAINT bundle_component_not_itself CHECK (
        bundle_product_id <> component_product_id),

    -- Zero of something is not a component, and a negative quantity would take a negative share of
    -- the bundle's value — which is a different bundle, not this one at a discount.
    CONSTRAINT bundle_component_quantity_positive CHECK (quantity_per_bundle > 0)
);

COMMENT ON TABLE bundle_component IS
    'The component list of a bundle (brief §5). One level deep: a component may not itself be a '
    'bundle, which makes cycles impossible by construction. Enforced in BundleService, since a CHECK '
    'cannot read the other row''s flag.';

CREATE INDEX bundle_component_bundle_idx ON bundle_component (bundle_product_id);

-- Answers "which bundles break if this product goes?", which is what ProductService.deactivate has to
-- ask before discontinuing a component.
CREATE INDEX bundle_component_component_idx ON bundle_component (component_product_id);

-- ---------------------------------------------------------------------------------------
-- A note on ProductType, which is NOT constrained here
-- ---------------------------------------------------------------------------------------
-- A bundle may be GOODS or SERVICE, and its components may be either. A machine sold with its
-- installation is a real bundle, and the installation has revenue allocated to it and nothing to take
-- off a shelf. So availability is the minimum over the STOCKED components only, and a bundle with no
-- stocked components is not limited by stock at all — InventoryService refuses to answer zero for one,
-- because zero would say the opposite of what is true.
