/**
 * A calendar date as the wire wants it — **from local components, never through an instant**.
 *
 * ## ⚠️ This exists because of a real defect, not as a precaution
 *
 * The obvious spelling is `new Date().toISOString().slice(0, 10)`, and it is wrong for a *calendar*
 * date. `toISOString` converts to UTC first, so local midnight on 1 January in Athens (UTC+2) becomes
 * `2025-12-31T22:00Z` and the date comes out as **the previous day**. F5's sales invoice list opened
 * its default range on `2025-12-31` instead of `2026-01-01`, and `sales.test.tsx` is what caught it.
 *
 * **West of UTC it fails the other way** — a date late rather than early — which on a year-to-date
 * range would silently omit 1 January's invoices. That is an accounting error, not a cosmetic one,
 * and it would have shown up once a year in a place nobody was looking.
 *
 * ## The principle, which outlives this function
 *
 * These fields are `LocalDate` on the backend: **a day, with no instant and no zone**. Converting one
 * through an instant is the mistake; the formatting is incidental. Any screen that needs "today" or
 * "the first of this year" for a date input has the same problem, so it is written once here.
 *
 * 📌 Reading in the other direction has the same trap and is not solved here:
 * `new Date('2026-01-01')` parses as **UTC midnight** and can render as 31 December in a negative
 * offset. Display currently goes through `toLocaleDateString`, which is where that would surface.
 */
export function localIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}
