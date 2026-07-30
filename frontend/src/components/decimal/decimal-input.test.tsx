import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'

import type { Money, Quantity } from '@/api/generated/model'

import { MoneyInput, QuantityInput } from './decimal-input'

function MoneyHarness({ currency = 'EUR' }: { currency?: string }) {
  const [value, setValue] = useState<Money | undefined>(undefined)
  return (
    <>
      <MoneyInput aria-label="amount" value={value} currency={currency} onValueChange={setValue} />
      <output data-testid="wire">{value ? `${value.amount} ${value.currency}` : 'empty'}</output>
    </>
  )
}

function QuantityHarness({ allowFractions }: { allowFractions: boolean }) {
  const [value, setValue] = useState<Quantity | undefined>(undefined)
  return (
    <>
      <QuantityInput aria-label="quantity" value={value} allowFractions={allowFractions} onValueChange={setValue} />
      <output data-testid="wire">{value ?? 'empty'}</output>
    </>
  )
}

describe('MoneyInput', () => {
  it('is never a number input', () => {
    // The prohibition, asserted on the rendered element rather than only in the lint rule.
    render(<MoneyHarness />)
    const field = screen.getByLabelText('amount')
    expect(field).toHaveAttribute('type', 'text')
    expect(field).toHaveAttribute('inputmode', 'decimal')
  })

  it('emits the canonical wire string from a full stop', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    await user.type(screen.getByLabelText('amount'), '12.5')
    expect(screen.getByTestId('wire')).toHaveTextContent('12.50 EUR')
  })

  it('accepts a comma, because a Greek keyboard produces one', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    await user.type(screen.getByLabelText('amount'), '12,5')
    expect(screen.getByTestId('wire')).toHaveTextContent('12.50 EUR')
  })

  it('keeps what was typed on screen while typing', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    const field = screen.getByLabelText('amount')
    await user.type(field, '12,')
    // Not reformatted mid-keystroke: the field shows what the person is typing.
    expect(field).toHaveValue('12,')
  })

  it('canonicalises on blur', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    const field = screen.getByLabelText('amount')
    await user.type(field, '12,5')
    await user.tab()
    expect(field).toHaveValue('12.50')
  })

  it('refuses letters entirely', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    const field = screen.getByLabelText('amount')
    await user.type(field, 'abc')
    expect(field).toHaveValue('')
    expect(screen.getByTestId('wire')).toHaveTextContent('empty')
  })

  it('shows an ambiguous value as invalid instead of reshaping it', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    const field = screen.getByLabelText('amount')
    await user.type(field, '1.234,56')

    // What was typed stays on screen. Dropping the comma as it arrived would leave `1.23456` —
    // a different number, reached silently, which is the failure this whole component exists to
    // prevent. The field is invalid and holds no value, so nothing can be submitted from it.
    expect(field).toHaveValue('1.234,56')
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByTestId('wire')).toHaveTextContent('empty')

    // And it survives blur rather than being wiped, so the mistake is visible and correctable.
    await user.tab()
    expect(field).toHaveValue('1.234,56')
  })

  it('carries the currency it was given rather than defaulting one', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness currency="GBP" />)
    await user.type(screen.getByLabelText('amount'), '3')
    expect(screen.getByTestId('wire')).toHaveTextContent('3.00 GBP')
  })

  it('empties to undefined rather than to zero', async () => {
    const user = userEvent.setup()
    render(<MoneyHarness />)
    const field = screen.getByLabelText('amount')
    await user.type(field, '5')
    await user.clear(field)
    // Zero is a value somebody meant. An empty field is the absence of one, and sending 0.00
    // for it would silently record a free line.
    expect(screen.getByTestId('wire')).toHaveTextContent('empty')
  })
})

describe('QuantityInput', () => {
  it('pads to six decimals', async () => {
    const user = userEvent.setup()
    render(<QuantityHarness allowFractions />)
    await user.type(screen.getByLabelText('quantity'), '3')
    expect(screen.getByTestId('wire')).toHaveTextContent('3.000000')
  })

  it('refuses a fraction for a unit that cannot be divided', async () => {
    const user = userEvent.setup()
    render(<QuantityHarness allowFractions={false} />)
    const field = screen.getByLabelText('quantity')
    await user.type(field, '2.5')

    // Half a box is not a quantity this unit of measure has. It is refused visibly — not rounded
    // to 2 or to 3, either of which would be this application deciding what somebody meant.
    expect(field).toHaveValue('2.5')
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByTestId('wire')).toHaveTextContent('empty')
  })

  it('allows a fraction where the unit permits one', async () => {
    const user = userEvent.setup()
    render(<QuantityHarness allowFractions />)
    await user.type(screen.getByLabelText('quantity'), '2,5')
    expect(screen.getByTestId('wire')).toHaveTextContent('2.500000')
  })
})
