# ADR 0005 — EUR-only behaviour, but currency is modelled from day one

**Date:** 2026-07-27
**Status:** Accepted

## Decision

Every monetary value in NovoCore is a `Money` value object carrying **both** a
`BigDecimal` amount and a currency, and every monetary database column is accompanied by a
currency column. All Phase 1 logic assumes EUR, and a non-EUR value is rejected at the
boundary rather than half-handled.

There is **no** exchange-rate handling, no functional-currency conversion, no FX gain or
loss on settlement, and no revaluation. Those are out of scope.

## Context

Multi-currency is never mentioned anywhere in the brief. But brief §6 refers to imports as
a routine case ("common on imports"), and imports commonly mean supplier invoices
denominated in a foreign currency. So the *possibility* of foreign currency is real even
though the requirement is absent.

The decision is about which mistake is cheaper to correct. Adding a currency column later
means migrating every amount column, every journal line, and every posting path in a system
that by then holds real financial history — the expensive kind of retrofit, on the most
correctness-critical tables in the database. Carrying a currency column now costs a few
bytes per row and one field on a value object.

Full multi-currency now was rejected as significant scope beyond anything the brief asks
for, and `CLAUDE.md`'s scope discipline rule forbids building it speculatively.

## Consequences

- `Money` is `(BigDecimal amount, Currency currency)`. There is no constructor that takes
  an amount alone with an implied currency, because that is exactly the shortcut that makes
  the currency column decorative.
- Arithmetic on two `Money` values with different currencies throws. It does not convert,
  and it does not silently pick one.
- Phase 1 validates that incoming money is EUR and fails loudly otherwise, per
  `CLAUDE.md` rule 8's fail-loud principle. It does not silently coerce.
- A journal entry's debits and credits must balance **per currency**. In Phase 1 that is
  indistinguishable from balancing overall, since everything is EUR — but the invariant is
  written per-currency so that the structural guarantee in `CLAUDE.md` rule 6 does not
  quietly weaken if multi-currency is ever switched on.
- Should real multi-currency ever be needed, the work is additive (rates, a functional
  amount per line, FX accounts) rather than a migration of existing columns.

## Related

`CLAUDE.md` rule 5 — money is always `BigDecimal`, never `double` or `float`. `Money`
is the single place that rule is expressed, and an ArchUnit rule forbids `float`/`double`
fields and parameters across the codebase so it cannot be bypassed.
