import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { SearchFilter } from './search-filter'

/**
 * The search box's own behaviour, separately from any screen.
 *
 * Three things are asserted here that a screen test would only ever assert incidentally, and that
 * every one of the five screens depends on being true.
 */

function renderBox(onChange: (term: string | undefined) => void) {
  return render(
    <SearchFilter id="test-search" onChange={onChange} label="Search" placeholder="Type…" />,
  )
}

describe('the search box', () => {
  it('reports the term once, after the typing stops', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderBox(onChange)

    await user.type(screen.getByLabelText('Search'), 'Cof')

    // The visible value is not debounced — the box must never feel laggy.
    expect(screen.getByLabelText('Search')).toHaveValue('Cof')

    await waitFor(() => expect(onChange).toHaveBeenCalledWith('Cof'))
    // One request per pause, not one per character. Without the debounce this is three query-key
    // changes, each of which walks the list screen through the render path the loop guard defends.
    expect(onChange.mock.calls.filter(([term]) => term !== undefined)).toHaveLength(1)
  })

  it('reports undefined when the box is cleared, never an empty string', async () => {
    // Both mean "no filter" to the backend, but only `undefined` keeps the parameter out of the
    // query key — so clearing the box returns to the query the screen started on rather than to a
    // second, identical one cached separately.
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderBox(onChange)

    const box = screen.getByLabelText('Search')
    await user.type(box, 'Cof')
    await waitFor(() => expect(onChange).toHaveBeenCalledWith('Cof'))

    await user.clear(box)

    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith(undefined))
    expect(onChange).not.toHaveBeenCalledWith('')
  })

  it('trims the term, so a trailing space from a paste is not a different search', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderBox(onChange)

    await user.type(screen.getByLabelText('Search'), '  Cof  ')

    await waitFor(() => expect(onChange).toHaveBeenCalledWith('Cof'))
  })

  it('keeps debouncing when the parent re-renders with a new inline callback', async () => {
    /*
     * The defect this guards. Every call site passes `onChange={setSearch}` or an inline arrow, and
     * a list screen re-renders constantly — on every query state change, and on every filter tick.
     * With the callback named as an effect dependency, each of those renders clears and restarts the
     * timer, so on a busy screen the debounce never elapses and the search silently never fires.
     */
    const onChange = vi.fn<(term: string | undefined) => void>()
    const user = userEvent.setup()

    function Parent() {
      return (
        <SearchFilter
          id="s"
          onChange={(term) => {
            onChange(term)
          }}
          label="Search"
          placeholder=""
        />
      )
    }

    const { rerender } = render(<Parent />)
    await user.type(screen.getByLabelText('Search'), 'Cof')
    // A fresh arrow identity on each of these, exactly as a real re-render produces.
    rerender(<Parent />)
    rerender(<Parent />)

    await waitFor(() => expect(onChange).toHaveBeenCalledWith('Cof'))
  })
})
