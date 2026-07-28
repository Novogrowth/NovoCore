-- Step 6: inventory lots, serialized units, and the Location that decides sellability.
--
-- ---------------------------------------------------------------------------------------
-- Q7, ANSWERED: STOCK PER LOCATION, PLUS A COMPUTED SELLABLE FIGURE
-- ---------------------------------------------------------------------------------------
-- Sellable stock is the Inventory location only, excluding Damaged Goods and Service.
--
-- This is why V9 refused to put a stock column on `product` and why none appears here either. Stock
-- is the sum of what its lots have left, grouped by where that stock is, computed on every read
-- (brief §5: never stored). Seven on the shelf and seven in Damaged Goods are the same total and
-- opposite answers to "can I sell this?", so a single number has to pick one of them to be wrong
-- about.
--
-- ---------------------------------------------------------------------------------------
-- TWO SHAPES OF LOT, AND WHY THE QUANTITY COLUMNS ARE NULLABLE
-- ---------------------------------------------------------------------------------------
-- Brief §5: serialized products get individual units within a lot, and a correction or write-off on
-- one refers to that unit and its own real cost, with no FIFO logic, because it is not pooled stock.
--
-- So a lot is one of two things, and the difference is structural rather than cosmetic:
--
--   POOLED         quantity_received / quantity_remaining hold the numbers, `location` holds where
--                  that stock sits, and there are no serialized_unit rows.
--   SERIAL-TRACKED all three of those columns are NULL, and the lot's quantity IS the count of its
--                  units. Location lives on each unit, because three identical machines received
--                  together are one lot and one of them going out for repair does not move the
--                  other two.
--
-- The nullability is the point, not a convenience. Storing a quantity on a serial-tracked lot would
-- be a second copy of a number the units already state, free to disagree with them after the first
-- sale — the same argument that keeps `normal_balance_side` out of `account`, a useful life off
-- `asset`, and a cost off `asset` entirely. The consistent rule across both shapes:
-- LOCATION LIVES WHEREVER THE QUANTITY DOES.
--
-- One CHECK enforces the pairing so that no third shape exists: a lot with a quantity and no
-- location, or units and a quantity, is refused by the database and not merely by the service.
--
-- ---------------------------------------------------------------------------------------
-- THE FIRST numeric(19,6) MONETARY COLUMN
-- ---------------------------------------------------------------------------------------
-- `unit_cost` is a unit cost, which V1's convention already named as a multiplier: numeric(19,6),
-- because brief §4's proportional landed-cost allocation produces repeating decimals per unit and
-- rounding them before they are multiplied back out overstates the lot for no traceable reason.
-- It is also the first numeric(19,6) column that is MONETARY — the others so far are a VAT rate, a
-- depreciation rate and a quantity, none of which have a currency.
--
-- So it carries a `unit_cost_currency char(3)` companion, exactly as V9 established for
-- numeric(19,2) amounts, and SchemaConventionsIT has been widened to require one for any
-- numeric(19,6) column whose name ends in `_cost`. The scale alone cannot be the discriminator:
-- a rate and a quantity share it and are not money.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DELIBERATELY ABSENT
-- ---------------------------------------------------------------------------------------
-- * NO SOURCE DOCUMENT REFERENCE. Brief §5 lists one, and ADR 0004 already settles that the Goods
--   Receipt is what creates a lot — but neither the Purchase Invoice nor the Goods Receipt exists
--   until step 8. A nullable column added now would spend a whole step meaning nothing, so the
--   reference is a step 8 obligation instead.
-- * NO WRITE-OFF TABLE, although Q25's reason enum (WriteOffReason: SHRINKAGE / DAMAGE / EXPIRY /
--   OTHER) is built in core-api this step. A write-off derecognises an asset, so it is a posting,
--   and the journal engine is step 7. Reducing a lot's quantity here without the entry would leave
--   the stock gone and the balance sheet still carrying it at cost — strictly worse than not
--   building it.
-- * NO AVERAGE COST, and no valuation column anywhere. A lot's value is its remaining quantity
--   extended at its unit cost, and the Inventory control account's balance is the sum of its
--   journal lines from step 7.
-- * NOTHING DECIDES Q17 (may stock go negative in aggregate). A single lot cannot go below zero,
--   by CHECK. The aggregate policy belongs with the consumption that could breach it, in step 8.
-- * NO WAREHOUSE ENTITY. The three locations are states of stock, not places on a map. Several
--   physical warehouses are a different concept and are not in the brief.

-- ---------------------------------------------------------------------------------------
-- Products gain the serial-tracking flag
-- ---------------------------------------------------------------------------------------
-- On `product` rather than only on the lot, because it decides how a receipt is even shaped, and
-- whoever creates the product knows the answer. The lot does NOT carry a copy of it: which shape a
-- lot is, is visible from whether its quantity columns are populated, so a copy would be a third
-- statement of the same fact.

ALTER TABLE product
    ADD COLUMN serial_tracked boolean NOT NULL DEFAULT false;

-- A service has no units to number. Not merely unhelpful — it would create a lot for something that
-- has no lots.
ALTER TABLE product
    ADD CONSTRAINT product_serial_tracked_needs_goods CHECK (
        serial_tracked = false OR product_type = 'GOODS');

COMMENT ON COLUMN product.serial_tracked IS
    'Whether stock is identified individually by serial number (brief §5). Decides the shape of this '
    'product''s lots, and cannot be changed once any lot exists — a pooled quantity of five cannot '
    'become five identified units, because the serial numbers were never recorded.';

-- ---------------------------------------------------------------------------------------
-- Inventory lots
-- ---------------------------------------------------------------------------------------

CREATE TABLE inventory_lot (
    id                     bigserial     PRIMARY KEY,

    product_id             bigint        NOT NULL,

    -- NULL exactly when the lot is serial-tracked; the unit count is the quantity then. See the
    -- header, and the shape CHECK below.
    quantity_received      numeric(19,6),
    quantity_remaining     numeric(19,6),

    -- The cost of one unit. Six decimals because it is a multiplier, not a posted amount — brief §4's
    -- landed-cost allocation is what makes that necessary rather than tidy. Zero is allowed: a
    -- supplier's free sample is a real lot, and unlike a zero SELLING price it gives nothing away.
    unit_cost              numeric(19,6) NOT NULL,
    unit_cost_currency     char(3)       NOT NULL,

    -- When the stock became ours. The first key of FIFO order; `id` breaks ties within a day.
    acquisition_date       date          NOT NULL,

    -- Coffee. NULL for everything else, and the reason brief §9 can have a Roast Date Report.
    -- Deliberately not constrained against the acquisition date: we roast in-house, so a roast date
    -- after acquisition of the green beans is the normal case, and buying already-roasted coffee puts
    -- it before. Neither ordering is wrong.
    roast_date             date,

    -- A gr.novotrade.novocore.core.api.inventory.StockLocation name. Constrained by CHECK rather than
    -- a lookup table, for V6 and V9's reason: the authoritative list is the Java enum, because each
    -- value has behaviour only NovoCore can supply — INVENTORY is what sellability is computed from
    -- and DAMAGED_GOODS is what phase 8's Clearing Checks must single out. A row added at runtime
    -- would be storable and unhandled.
    --
    -- NULL exactly when the lot is serial-tracked; its units carry their own location then.
    location               varchar(20),

    created_at             timestamptz   NOT NULL DEFAULT now(),
    created_by             varchar(100)  NOT NULL DEFAULT 'system',
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    updated_by             varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT inventory_lot_product_fk
        FOREIGN KEY (product_id) REFERENCES product (id),

    CONSTRAINT inventory_lot_location_known CHECK (
        location IS NULL OR location IN ('INVENTORY', 'SERVICE', 'DAMAGED_GOODS')),

    -- The two shapes, and no third one. A lot either holds pooled quantity at a location, or holds
    -- serialized units that carry their own — so these three columns are populated together or absent
    -- together. Enforced here as well as in the service, so it holds against a manual psql session
    -- (migration README rule 4).
    CONSTRAINT inventory_lot_pooled_columns_go_together CHECK (
        (quantity_received IS NULL) = (quantity_remaining IS NULL)
        AND (quantity_received IS NULL) = (location IS NULL)),

    -- A lot of nothing is not a receipt. Negative would be a reversal, which is a posting and not a
    -- lot.
    CONSTRAINT inventory_lot_received_positive CHECK (
        quantity_received IS NULL OR quantity_received > 0),

    -- Bounded at both ends. Above received would mean more left than ever arrived; below zero would be
    -- stock consumed twice. Note this refuses a NEGATIVE LOT, which is not the same as deciding Q17:
    -- whether aggregate stock across lots may go negative is step 8's question.
    CONSTRAINT inventory_lot_remaining_within_received CHECK (
        quantity_remaining IS NULL
        OR (quantity_remaining >= 0 AND quantity_remaining <= quantity_received)),

    CONSTRAINT inventory_lot_unit_cost_not_negative CHECK (unit_cost >= 0),

    -- ADR 0005, the same biconditional V9 established for numeric(19,2). Both sides are NOT NULL
    -- here, so it can only ever hold — stated anyway, because the convention is what the next
    -- monetary column will be read against, and an exception that "did not need it" is how a
    -- convention stops being one.
    CONSTRAINT inventory_lot_unit_cost_has_currency CHECK (
        (unit_cost IS NULL) = (unit_cost_currency IS NULL))
);

COMMENT ON TABLE inventory_lot IS
    'The INVENTORY_LOT sub-ledger behind the Inventory control account. Every purchase creates a lot '
    '(brief §5). Two shapes: pooled stock holds its quantity and location here, serial-tracked stock '
    'holds neither and its units hold both.';

COMMENT ON COLUMN inventory_lot.unit_cost IS
    'Cost of one unit, numeric(19,6) with a char(3) currency companion. Six decimals because it is a '
    'multiplier: brief §4''s proportional landed-cost allocation (step 10) produces repeating '
    'decimals, and rounding before extending overstates the lot. Until step 10 this is exactly what '
    'was paid.';

COMMENT ON COLUMN inventory_lot.location IS
    'StockLocation of pooled stock. NULL when serial-tracked — the units carry it. Only INVENTORY is '
    'sellable (Q7). Moving to DAMAGED_GOODS posts NOTHING, which is why phase 8 Clearing Checks must '
    'surface lots aging there: nothing else forces the write-off that derecognises them.';

-- FIFO order, stated once as an index so there is one definition of it. Step 8 consumes in exactly
-- this order; `id` is the tie-break within a single acquisition date.
CREATE INDEX inventory_lot_fifo_idx
    ON inventory_lot (product_id, acquisition_date, id);

-- The open lots are the only ones FIFO and stock levels look at, and exhausted lots accumulate
-- forever, so the working set is worth its own partial index.
CREATE INDEX inventory_lot_open_idx
    ON inventory_lot (product_id) WHERE quantity_remaining > 0;

-- Reads the Damaged Goods aging check (phase 8) and the per-location stock query.
CREATE INDEX inventory_lot_location_idx
    ON inventory_lot (location) WHERE location IS NOT NULL;

-- ---------------------------------------------------------------------------------------
-- Serialized units
-- ---------------------------------------------------------------------------------------

CREATE TABLE serialized_unit (
    id                bigserial    PRIMARY KEY,

    lot_id            bigint       NOT NULL,

    -- UNIQUE across all stock. Strictly this is a stronger claim than the world supports — two
    -- manufacturers could issue the same string — and it is still the right constraint: within one
    -- business's stock the same serial appearing twice is overwhelmingly a duplicate scan or a unit
    -- received twice, and catching that is worth more than accommodating a collision nobody has seen.
    -- A real one becomes a per-product uniqueness rule as a deliberate migration, rather than being
    -- discovered as a silent overwrite of a warranty record.
    serial_number     varchar(80)  NOT NULL,

    -- A gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus name. Only IN_STOCK is
    -- reachable in step 6: SOLD needs the Sales Invoice that names the buyer (step 9) and WRITTEN_OFF
    -- needs the posting that derecognises the unit (step 7). All three are listed from the start
    -- because the stock count is written against this column now, so it is already right on the day a
    -- unit is sold.
    status            varchar(20)  NOT NULL DEFAULT 'IN_STOCK',

    -- Each unit's own location, which is the whole reason a serial-tracked lot has none: one machine
    -- going out for repair must not move its lot-mates.
    location          varchar(20)  NOT NULL,

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        varchar(100) NOT NULL DEFAULT 'system',
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT serialized_unit_lot_fk
        FOREIGN KEY (lot_id) REFERENCES inventory_lot (id),

    CONSTRAINT serialized_unit_serial_number_unique UNIQUE (serial_number),

    CONSTRAINT serialized_unit_serial_number_not_blank CHECK (btrim(serial_number) <> ''),

    CONSTRAINT serialized_unit_status_known CHECK (
        status IN ('IN_STOCK', 'SOLD', 'WRITTEN_OFF')),

    CONSTRAINT serialized_unit_location_known CHECK (
        location IN ('INVENTORY', 'SERVICE', 'DAMAGED_GOODS'))
);

COMMENT ON TABLE serialized_unit IS
    'One individually identified item within a lot (brief §5). Not pooled: a write-off or count '
    'correction names the unit and uses its own actual cost, with no FIFO logic. NO customer or '
    'invoice link yet — brief §5 wants one once sold, and that is step 9, because a unit marked sold '
    'with no document behind it is a claim nothing can substantiate.';

CREATE INDEX serialized_unit_lot_idx ON serialized_unit (lot_id);

-- Stock on hand per location, for the serial-tracked half of the stock query.
CREATE INDEX serialized_unit_on_hand_idx
    ON serialized_unit (location) WHERE status = 'IN_STOCK';

-- ---------------------------------------------------------------------------------------
-- A permission section for inventory
-- ---------------------------------------------------------------------------------------
-- Separate from PRODUCTS because of cost. Stock LEVELS are a product-level read — Remote/Order Staff
-- has VIEW on Products and genuinely needs to know whether there are three left. A LOT carries its
-- unit cost, which is precisely what PRODUCT_LAST_PURCHASE_PRICE exists to keep from that role, so
-- granting the ability to see stock must not grant the ability to see what it cost.
--
-- NO GRANTS ARE SEEDED. Access is default-deny, so INVENTORY is invisible to Remote/Order Staff
-- without saying so, and visible to Owner and Admin at once through the full_access flag.

ALTER TABLE role_section_grant
    DROP CONSTRAINT role_section_grant_section_known;

ALTER TABLE role_section_grant
    ADD CONSTRAINT role_section_grant_section_known CHECK (section IN (
        'CHART_OF_ACCOUNTS',
        'TAX_AND_CHARGES',
        'SETTINGS',
        'AUDIT_LOG',
        'USERS_AND_ROLES',
        'PRODUCTS',
        'CUSTOMERS',
        'SUPPLIERS',
        'FIXED_ASSETS',
        'INVENTORY',
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS'));
