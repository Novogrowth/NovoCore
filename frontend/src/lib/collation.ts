import { Decimal, fromWire } from './decimal'

/**
 * How text is ordered for a human reading a list.
 *
 * ## Why this file exists at all
 *
 * `[].sort()` on strings compares UTF-16 code units, and PostgreSQL under this deployment's
 * `--locale=C` compares bytes. They are the same wrongness, and it is not subtle:
 *
 * ```
 * Apple | Banana | Zebra | apple | banana | zebra | Ácme | Öl | Άλφα | Αθήνα | Ωμέγα | αθήνα | ζήτα
 * ```
 *
 * Every uppercase word before every lowercase one, every accented word after all unaccented ones,
 * and **every Greek name after every Latin one** — with `Ωμέγα` ahead of `αθήνα` inside Greek
 * because capitals occupy the lower code points. Nobody reading a customer list means that.
 *
 * ⚠️ **`pg_c_utf8` does not fix it, and it is the obvious thing to reach for.** S1 introduced
 * `lower(… COLLATE pg_c_utf8)` so Greek capitals fold, and it would be reasonable to conclude the
 * collation question is therefore settled. It is not: `pg_c_utf8` changes **case mapping**, not
 * **sort order**, and its `ORDER BY` output is character-for-character identical to `C`'s. Measured
 * on the running stack, not inferred.
 *
 * ## The order this produces
 *
 * `Intl.Collator('el')` — Greek block first, then Latin, each alphabetised case- and
 * accent-correctly:
 *
 * ```
 * αθήνα | Αθήνα | Άλφα | Βήτα | ελλάς | Ελλάς | ζήτα | Ωμέγα ‖ Ácme | apple | Apple | Öl | Zebra
 * ```
 *
 * Greek first is a **decision, taken 2026-08-01**, not a default: these are a Greek company's
 * records read by Greek operators, so a customer list should open on the Greek names. It is fixed
 * rather than following the account's language, because a list whose row order changes when
 * somebody switches UI language is worse than one that does not.
 *
 * ## It is deliberately the same order the database will produce
 *
 * These lists sort in the browser today because none of the five endpoints pages or sorts on the
 * server. When they do, the backend's `ORDER BY … COLLATE "el-GR-x-icu"` must not disagree with
 * this — a list that reorders itself the day paging lands is a defect nobody would attribute to
 * paging. The two were checked against each other rather than assumed: PostgreSQL 17.10 with
 * ICU 78.1 under `el-GR-x-icu`, and Node 24 with ICU 78.3 under `Intl.Collator('el')`, return
 * **byte-identical orderings** of the sample above. Both read the same CLDR data.
 *
 * ⚠️ **Numeric ordering is deliberately NOT enabled.** `{ numeric: true }` would put
 * `TEST-PRODUCT-2` ahead of `TEST-PRODUCT-10`, which is what a person wants — but PostgreSQL's
 * stock `el-GR-x-icu` does not do it, so switching it on here would buy a nicer order in the
 * browser at the cost of the two halves disagreeing. Matching it server-side needs a custom
 * `CREATE COLLATION … locale = 'el-GR-u-kn-true'`, which is a backend decision and a migration.
 * Until then the two sides say the same thing, which is worth more than the niceness.
 *
 * ⚠️ **This depends on the runtime having full ICU data.** A build against small-icu would silently
 * fall back to the root locale and lose the Greek-first reordering — the same shape as the test
 * database that was configured unlike the real one. `collation.test.ts` asserts the resolved locale
 * is actually `el`, so a stripped runtime fails the build instead of quietly reordering the lists.
 */

/**
 * Built once. Constructing an `Intl.Collator` is expensive and a table comparator is called
 * O(n log n) times per sort; a fresh one per comparison made a 5,000-row sort visibly slow in
 * every implementation that has ever done it.
 */
const collator = new Intl.Collator('el')

/** The locale this module actually resolved to. Read by its test, not by application code. */
export const RESOLVED_COLLATION_LOCALE = collator.resolvedOptions().locale

/**
 * Compares two display strings.
 *
 * **Absent sorts last, in both directions**, and that is a display decision rather than a
 * comparison one: "not set" is not a value that belongs at either alphabetical end, and a column
 * sorted descending that opens on a screen of blanks looks broken. The table's `sortUndefined`
 * option enforces it above this function; this handles the case anyway so the comparator is
 * correct when called directly.
 */
export function compareText(a: string | undefined | null, b: string | undefined | null): number {
  if (a === undefined || a === null || a === '') return b === undefined || b === null || b === '' ? 0 : 1
  if (b === undefined || b === null || b === '') return -1
  return collator.compare(a, b)
}

/**
 * Compares two decimal values that came off the wire, as numbers.
 *
 * ⚠️ **A wire decimal is a string, and comparing two of them as text is wrong**, not merely
 * imprecise: `"9.00"` sorts after `"1234.56"` because `'9'` is above `'1'`. Money is a string on
 * this side of the wire for the reasons `decimal.ts` gives, and sorting is one of the places that
 * fact has to be honoured rather than worked around — which is also why this cannot use
 * `parseFloat`, banned by ESLint.
 */
export function compareDecimal(a: string | undefined, b: string | undefined): number {
  if (a === undefined) return b === undefined ? 0 : 1
  if (b === undefined) return -1
  return fromWire(a).comparedTo(fromWire(b))
}

/**
 * Compares two monetary amounts.
 *
 * **Grouped by currency first, then by amount.** Ordering 100 USD against 90 EUR by the number
 * alone states a conversion nobody performed, at a rate nobody chose — the same objection that
 * keeps the currency guard on every binary operation in `Money`. Grouping is the honest answer for
 * a column that has to be sorted somehow: within a currency the order is exact, and across
 * currencies the screen shows that it did not pretend to compare them.
 */
export function compareMoney(
  a: { amount?: string; currency?: string } | undefined,
  b: { amount?: string; currency?: string } | undefined,
): number {
  if (a?.amount === undefined) return b?.amount === undefined ? 0 : 1
  if (b?.amount === undefined) return -1

  const byCurrency = compareText(a.currency, b.currency)
  return byCurrency !== 0 ? byCurrency : new Decimal(fromWire(a.amount)).comparedTo(fromWire(b.amount))
}
