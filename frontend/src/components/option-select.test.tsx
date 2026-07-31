import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { idOptions } from '@/api/lookups'
import '@/i18n'

import { OptionSelect } from './option-select'

/**
 * The closed trigger has to say what was chosen.
 *
 * The defect this exists for was invisible in the popup and only wrong on the trigger: Base UI's
 * `Select.Value` resolves a label by looking the value up in the root's `items`, and with no
 * `items` it renders `String(value)` without complaining. So the create form said *"Unit: 4"* and
 * *"Type: GOODS"*, and the header said *"en"* — while every dropdown, built from the same list,
 * showed the right words.
 *
 * These assert on the **trigger's own text**, which is the thing that was wrong. A test that opened
 * the popup and looked at the options would have passed against the defect.
 */
describe('OptionSelect', () => {
  it('shows the label of the chosen option, not its value', () => {
    render(
      <OptionSelect
        aria-label="Unit"
        options={[
          { value: '4', label: 'Gram' },
          { value: '5', label: 'Kilogram' },
        ]}
        value="4"
        onValueChange={vi.fn()}
      />,
    )

    const trigger = screen.getByLabelText('Unit')
    expect(trigger).toHaveTextContent('Gram')
    // The id must not leak onto the screen. "4" is what shipped, and it means nothing to anybody.
    expect(trigger).not.toHaveTextContent('4')
  })

  it('shows an enum label rather than the enum constant', () => {
    render(
      <OptionSelect
        aria-label="Type"
        options={[
          { value: 'GOODS', label: 'Goods' },
          { value: 'SERVICE', label: 'Service' },
        ]}
        value="GOODS"
        onValueChange={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Type')).toHaveTextContent('Goods')
    expect(screen.getByLabelText('Type')).not.toHaveTextContent('GOODS')
  })

  it('shows nothing at all when nothing is chosen', () => {
    render(
      <OptionSelect
        aria-label="Supplier"
        options={[{ value: '7', label: 'Coffee Importers SA' }]}
        value={null}
        onValueChange={vi.fn()}
      />,
    )

    // Blank, not the id of nothing. (The trigger still carries its caret glyph, so this asserts
    // the absence of the value rather than an empty element.)
    expect(screen.getByLabelText('Supplier')).not.toHaveTextContent('7')
    expect(screen.getByLabelText('Supplier')).not.toHaveTextContent('Coffee Importers SA')
  })

  it('renders one option per item, so the popup and the trigger cannot disagree', () => {
    // The reason the option list is a single prop rather than an `items` prop beside the children:
    // two lists that have to match eventually do not.
    const options = [
      { value: '1', label: 'One' },
      { value: '2', label: 'Two' },
    ]
    render(<OptionSelect aria-label="Count" options={options} value="2" onValueChange={vi.fn()} />)

    expect(screen.getByLabelText('Count')).toHaveTextContent('Two')
  })
})

/** How every lookup arrives: both fields optional, because a withheld one is simply absent. */
interface Reference {
  id?: number
  name?: string
}

describe('idOptions', () => {
  it('turns reference data into options, id as the value and name as the label', () => {
    expect(idOptions([{ id: 4, name: 'Gram' }], (unit) => unit.name)).toEqual([
      { value: '4', label: 'Gram' },
    ])
  })

  it('falls back to the id when the name is withheld or missing', () => {
    // Same reasoning as `nameFor`: "#4" is something an operator can quote to somebody who can see
    // it, where an empty option is one nobody can choose on purpose.
    expect(idOptions<Reference>([{ id: 4 }], (unit) => unit.name)).toEqual([
      { value: '4', label: '#4' },
    ])
  })

  it('drops an item with no id rather than giving it a value of "undefined"', () => {
    expect(idOptions<Reference>([{ name: 'Nameless' }], (item) => item.name)).toEqual([])
  })
})
