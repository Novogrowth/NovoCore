/**
 * Compile-time proof that the wire format survived code generation.
 *
 * CLAUDE.md rule 5 says money is never a float. The backend enforces it — `Money`, `UnitCost`,
 * `Quantity` and `Rate` serialise as strings and a JSON number is refused — and step 16a's
 * acceptance test proves the OpenAPI spec says so. This file closes the last gap in that chain:
 * that **orval's TypeScript output** still says so, after a regeneration, an orval upgrade, or a
 * change to the config.
 *
 * Nothing here runs. `tsc` is the assertion: each `@ts-expect-error` line fails the build the day
 * the type on the line below it stops rejecting a number.
 */
import type { Money, Quantity, Rate, UnitCost } from './generated/model'

// A quantity is a string carrying six decimals, exactly as written.
const quantity: Quantity = '3.000000'
// @ts-expect-error a quantity is never a JSON number — 3.000000 would arrive as 3
const quantityAsNumber: Quantity = 3.0

// A rate is a string. 24% is '24.000000', not 24.
const rate: Rate = '24.000000'
// @ts-expect-error a rate is never a JSON number
const rateAsNumber: Rate = 24

// Money is an amount and a currency, and the amount is a string. The currency is never defaulted
// and never omitted, which is why it is not optional here either.
const money: Money = { amount: '12.50', currency: 'EUR' }
// @ts-expect-error an amount is never a JSON number — 12.50 cannot be represented exactly
const moneyAsNumber: Money = { amount: 12.5, currency: 'EUR' }
// @ts-expect-error currency is never omitted
const moneyWithoutCurrency: Money = { amount: '12.50' }

// A unit cost carries six decimals rather than two, and is otherwise Money's twin.
const unitCost: UnitCost = { amount: '12.505000', currency: 'EUR' }
// @ts-expect-error a unit cost amount is never a JSON number
const unitCostAsNumber: UnitCost = { amount: 12.505, currency: 'EUR' }

// Referenced so `noUnusedLocals` does not delete the assertions this file exists to make.
export const wireFormatAssertions = {
  quantity,
  quantityAsNumber,
  rate,
  rateAsNumber,
  money,
  moneyAsNumber,
  moneyWithoutCurrency,
  unitCost,
  unitCostAsNumber,
}
