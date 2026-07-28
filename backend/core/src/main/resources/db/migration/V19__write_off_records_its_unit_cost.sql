-- Step 10, part two: the landed-cost catch-up on returned stock, and the historical cost a write-off
-- reversal has to be checked against. ADR 0011.
--
-- ---------------------------------------------------------------------------------------
-- WHAT V18 BROKE, AND HOW IT WAS FOUND
-- ---------------------------------------------------------------------------------------
-- Before V18 a lot's unit cost never changed, so "the cost stock left at" and "the cost the lot
-- carries now" were the same number and nothing had to tell them apart. V18 made a lot's carrying
-- cost move, and three operations put stock BACK into a lot:
--
--   InventoryService.returnConsumed      goods came back from a customer
--   InventoryService.reverseConsumption  the sale should never have been recorded
--   InventoryService.reverseWriteOff     the loss should never have been recognised
--
-- All three restore quantity at the cost the stock LEFT at — deliberately, so that a later cost change
-- cannot revalue stock through a credit note (ADR 0009). But if a freight allocation landed while the
-- stock was out, the lot now carries those units at a higher figure than what was debited back, and
-- the Inventory control account and the lot's valuation disagree permanently.
--
-- Measured, not theorised. Lot of 10 at €10, sell 2, allocate €100 of freight (€80 capitalised on the
-- 8 remaining, €20 to Landed cost variance for the 2 that were gone), then bring the 2 back:
--
--   lot valuation      10 units × €20 = €200.00
--   Inventory ledger   100 − 20 + 80 + 20 = €180.00      <- €20 short, and it never clears
--
-- The €20 is exactly the variance posted on the grounds that those units had gone. They came back.
--
-- ---------------------------------------------------------------------------------------
-- ADR 0011: RETURNS CATCH UP, REVERSALS REFUSE
-- ---------------------------------------------------------------------------------------
-- The two cases are not the same fact and do not get the same answer.
--
-- A RETURN says the sale was real and the goods came back. It is an ordinary, operator-facing event
-- driven by a credit note, and the person processing it is not the person who allocates freight — so
-- refusing it would be refusing routine business over a document somebody else has not filed yet. It
-- posts the catch-up instead: debit Inventory, credit Landed cost variance, for
--
--     returned quantity × (the lot's allocated landed cost NOW
--                          − the allocated landed cost embedded in what the stock left at)
--
-- which is €20 in the example above, and zero whenever the lot's cost has not moved. Cost of goods
-- sold is still credited exactly what was debited, so ADR 0009's rule is untouched; the correction
-- lands where the original statement was made, and Landed cost variance goes back to meaning what it
-- claims — freight attributable to stock that has gone FOR GOOD.
--
-- A REVERSAL says the movement never happened. If that is true then those units were in the lot all
-- along, so the allocation's own split was wrong and not merely its counterpart: it should have
-- capitalised the whole share instead of writing part of it off. Patching the difference would leave
-- a posted allocation that states something untrue about which stock was where. So both reversal
-- paths REFUSE once the lot has been re-costed since, and the refusal names the remedy — which is
-- exact rather than a dead end:
--
--     reverse the freight allocation   (permitted: the lot's remaining quantity is unchanged)
--     reverse the consumption or write-off
--     allocate the freight again       (now capitalising the whole share, correctly)
--
-- That is ADR 0008's stance applied where it belongs and not where it does not.
--
-- ---------------------------------------------------------------------------------------
-- WHY THIS MIGRATION EXISTS: A WRITE-OFF HAS TO REMEMBER ITS COST
-- ---------------------------------------------------------------------------------------
-- The consumption path can already answer "what did this stock leave at" — stock_consumption_line
-- stores its unit_cost, and V16 said in as many words that it does so because step 10 would move a
-- lot's cost. The write-off cannot: V15 deliberately stored no amount, on the argument that a
-- write-off's amount is purely a consequence of our own posting and the entry is the honest source.
--
-- That argument was written when a lot's cost could not move. It can now, so the entry gives the
-- amount and no longer gives the COST — recovering it means dividing a rounded 2-decimal amount by a
-- quantity, which is not the 6-decimal figure that went in. The refusal above needs to compare the
-- lot's cost then against its cost now, exactly.
--
-- So `stock_write_off` now stores its unit cost, for precisely the reason `stock_consumption_line`
-- does. The amount is still NOT stored: that remains a consequence of the posting and is still read
-- back off the entry. One is a historical input, the other a historical output.
--
-- Backfilled from the lot's received cost, which is exact for every existing row: nothing could have
-- allocated a landed cost before V18, and V18 introduced no data of its own.

ALTER TABLE stock_write_off
    ADD COLUMN unit_cost          numeric(19,6),
    ADD COLUMN unit_cost_currency char(3);

UPDATE stock_write_off w
   SET unit_cost          = lot.received_unit_cost,
       unit_cost_currency = lot.received_unit_cost_currency
  FROM inventory_lot lot
 WHERE lot.id = w.lot_id;

-- Fail loudly rather than leaving a NULL that the NOT NULL below would reject anyway with a less
-- useful message — V16 and V18's stance about migrating a shape this file was not written against.
DO $$
DECLARE
    unbackfilled bigint;
BEGIN
    SELECT count(*) INTO unbackfilled FROM stock_write_off WHERE unit_cost IS NULL;
    IF unbackfilled > 0 THEN
        RAISE EXCEPTION
            'V19 could not backfill % write-off rows from their lot''s received cost. Every write-off '
            'names a lot and every lot has one, so this means the two tables disagree.', unbackfilled;
    END IF;
END $$;

ALTER TABLE stock_write_off
    ALTER COLUMN unit_cost SET NOT NULL,
    ALTER COLUMN unit_cost_currency SET NOT NULL;

ALTER TABLE stock_write_off
    -- UnitCost's rule, in the schema: zero is a real lot, negative is not a fact about any of them.
    ADD CONSTRAINT stock_write_off_unit_cost_not_negative CHECK (unit_cost >= 0),
    ADD CONSTRAINT stock_write_off_unit_cost_has_currency CHECK (
        (unit_cost IS NULL) = (unit_cost_currency IS NULL));

COMMENT ON COLUMN stock_write_off.unit_cost IS
    'What the lot was CARRIED at when this write-off derecognised it — received cost plus whatever '
    'landed cost had been allocated by then. Stored for stock_consumption_line.unit_cost''s reason: '
    'V18 made a lot''s carrying cost move, so recomputing it later would give a different answer with '
    'nothing to say which was historical. The posted AMOUNT is still not stored; that is a '
    'consequence of the entry and is read back off it (ADR 0011).';
