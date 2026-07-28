-- Close the blind spot in the VAT rate bound.
--
-- ---------------------------------------------------------------------------------------
-- WHAT WAS WRONG
-- ---------------------------------------------------------------------------------------
-- V5 bounded rate_percent to 0-100 and its comment claimed that this made "a rate mistakenly
-- entered as a fraction fail loudly instead of undercharging 100x". It did not. 0.24 written
-- for 24% sits comfortably inside 0-100, so it was accepted as a quarter of one percent and the
-- resulting invoice undercharged by exactly the factor the comment said was prevented. A test
-- (VatClassViewTest) even documented the gap honestly rather than closing it.
--
-- The same mistake was made and caught in step 5 on asset.depreciation_rate_percent, where the
-- fix was a lower bound of 1. This applies the same fix here, where an undercharge is worse:
-- brief §6 notes an undercharge is not recoverable from the customer once an invoice is issued.
--
-- ---------------------------------------------------------------------------------------
-- WHY "EXACTLY ZERO, OR AT LEAST ONE" RATHER THAN A PLAIN MINIMUM
-- ---------------------------------------------------------------------------------------
-- A flat `>= 1` would be wrong: the 0% class ('0', Μηδενικός Συντελεστής ΦΠΑ) is real, seeded,
-- and legally distinct from an exempt line. Zero is a legitimate rate; anything strictly between
-- zero and one is not. No VAT regime charges a fraction of a percent, so the whole interval
-- (0, 1) is unreachable by legitimate data and is therefore available as a trap for exactly the
-- factor-of-100 error: 0.24, 0.13, 0.06 and 0.04 all now fail instead of silently undercharging.
--
-- If a statute ever introduces a sub-1% rate, raising this is a deliberate migration with a
-- reason attached rather than a value nobody chose.
--
-- Every seeded rate (0, 3, 4, 4, 6, 9, 13, 17, 24) satisfies the new constraint, so this needs
-- no data change.

ALTER TABLE vat_class
    DROP CONSTRAINT vat_class_rate_is_a_percentage;

ALTER TABLE vat_class
    ADD CONSTRAINT vat_class_rate_is_a_percentage CHECK (
        rate_percent = 0
        OR (rate_percent >= 1 AND rate_percent <= 100));

COMMENT ON COLUMN vat_class.rate_percent IS
    'A percentage: 24% is 24.000000, not 0.24. numeric(19,6) as a multiplier, not an amount. '
    'Constrained to exactly 0 or 1-100, so a rate written as a fraction fails rather than '
    'undercharging by a factor of 100 — which a plain 0-100 range does not catch.';
