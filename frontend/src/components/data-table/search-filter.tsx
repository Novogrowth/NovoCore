import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * The search box every list screen uses, and the debounce behind it.
 *
 * One component rather than five copies, for the same reason the backend has one `TextSearch`: the
 * five screens would otherwise drift on the placeholder, the debounce interval, and — the one that
 * would actually matter — whether a cleared box sends `search=` or omits it.
 *
 * ## What it sends, and when
 *
 * The visible value updates on every keystroke; `onChange` fires on a **trailing** debounce. So the
 * box never feels laggy and the endpoint sees one request per pause rather than one per character.
 *
 * A cleared box reports `undefined`, not `''`. Both mean the unfiltered list to the backend — a
 * blank term is deliberately no filter — but `undefined` is what keeps the parameter out of the
 * query key, so clearing the box returns to the *same* cached query the screen started on rather
 * than to a second, identical one under a different key.
 *
 * ## ⚠️ Why the debounce is not optional here
 *
 * Every one of these lists is client-paged today (`useListState` reads that from the generated
 * capability map), so a filter change is a query-key change, which is the exact input to the render
 * loop `frontend/README.md` documents: a query with no data hands `DataTable` a fresh `[]`, which
 * rebuilds `useReactTable`'s row model, which reaches `setPage(0)`. That loop is defended against in
 * `useListState` and `unwrapList`, and it stays defended — but firing a key change per keystroke
 * means walking into that machinery ten times a word, and there is no reason to.
 */

/** Trailing debounce. Long enough to swallow a word, short enough not to feel deferred. */
const DEBOUNCE_MS = 250

export interface SearchFilterProps {
  /** Fires on the trailing edge of the debounce. `undefined` when the box is empty. */
  onChange: (term: string | undefined) => void
  /** Label text, already translated. Names what is searched, which differs per screen. */
  label: string
  /** Placeholder text, already translated. */
  placeholder: string
  /** Distinguishes the input from the others on a screen that ever has two. */
  id: string
}

export function SearchFilter({ onChange, label, placeholder, id }: SearchFilterProps) {
  const { t } = useTranslation('common')
  const [value, setValue] = useState('')

  /*
   * The callback is held in a ref rather than named as a dependency of the effect below. A call site
   * that passes an inline arrow — which every one of them does — would otherwise give the effect a
   * new dependency on every render, so the timer would be cleared and restarted each time and the
   * debounce would never elapse while anything else on the page was re-rendering.
   */
  const latest = useRef(onChange)
  useEffect(() => {
    latest.current = onChange
  })

  useEffect(() => {
    const trimmed = value.trim()
    const timer = setTimeout(
      () => latest.current(trimmed === '' ? undefined : trimmed),
      DEBOUNCE_MS,
    )
    return () => clearTimeout(timer)
  }, [value])

  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type="search"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder={placeholder}
        aria-label={label}
        className="w-64"
      />
      {/* Screen-reader-only, and it is the only statement anywhere that the match is a substring.
          Without it the box looks like every exact-match filter this application used to have. */}
      <span className="sr-only">{t('search.hint')}</span>
    </div>
  )
}
