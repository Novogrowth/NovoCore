import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { SegmentedControl } from './segmented-control'

/**
 * The two behaviours this component exists for, and neither is free from `ToggleGroup`.
 *
 * A grant is one of three values and always one of them; a toggle group, left alone, lets the
 * pressed item be pressed again and reports an empty selection — a state `PUT …/grants/{section}`
 * has no way to express.
 */

const OPTIONS = [
  { value: 'NONE', label: 'None' },
  { value: 'VIEW', label: 'View' },
  { value: 'FULL', label: 'Full', disabledReason: 'You hold View here.' },
]

function Harness({ onChange }: { onChange?: (value: string) => void }) {
  const [value, setValue] = useState('VIEW')
  return (
    <SegmentedControl
      aria-label="Sales"
      options={OPTIONS}
      value={value}
      onValueChange={(next) => {
        setValue(next)
        onChange?.(next)
      }}
    />
  )
}

describe('a segmented control', () => {
  it('cannot be emptied by pressing what is already pressed', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Harness onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: 'View' }))

    expect(onChange).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'View' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('reports the option that was chosen', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Harness onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: 'None' }))

    expect(onChange).toHaveBeenCalledWith('NONE')
  })

  it('shows an unavailable option disabled, with its reason, rather than hiding it', async () => {
    // The `lockedReason` half of the standing distinction, one level down: hiding a level that
    // exists on every other row leaves an administrator hunting for it.
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Harness onChange={onChange} />)

    const full = screen.getByRole('button', { name: 'Full' })
    expect(full).toBeDisabled()
    expect(full).toHaveAttribute('title', 'You hold View here.')

    await user.click(full)
    expect(onChange).not.toHaveBeenCalled()
  })
})
