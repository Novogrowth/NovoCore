import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type Me, type SettingView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { DocumentSettings, EmailSettings, RetentionSettings } from './settings-page'

/**
 * The three settings screens.
 *
 * The decisions these hold to, all three of which are about a control that must **not** be there:
 *
 * - **`cash.payment.limit` has no edit affordance at all** — not a disabled one. It is statutory,
 *   and a disabled control invites a hunt for the permission that unlocks it.
 * - **`smtp.password` never shows a value**, because the backend never sends one. The screen may say
 *   whether one is configured and nothing more.
 * - **There is no General page.** All 18 keys land on these three.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const setting = (key: string, value: string, extra: Partial<SettingView> = {}): SettingView => ({
  key,
  value,
  secret: false,
  description: `What ${key} does.`,
  updatedAt: '2026-07-28T17:21:41Z',
  updatedBy: 'system',
  ...extra,
})

/** The shape `GET /api/settings` returns — all 18, sorted by dotted key as the service sorts them. */
const settings: SettingView[] = [
  setting('attachment.max-size-bytes', '26214400'),
  setting('cash.payment.limit', '500.00'),
  setting('email.dispatch.batch-size', '20'),
  setting('email.max-attempts', '5'),
  setting('email.retention.inline-attachment-days', '90'),
  setting('email.retention.message-days', 'FOREVER'),
  setting('email.retry.backoff-max-seconds', '900'),
  setting('email.retry.backoff-seconds', '30'),
  setting('ledger.rounding.mode', 'HALF_UP'),
  setting('ledger.rounding.threshold', '0.03'),
  setting('smtp.from-address', 'erp@novotrade.gr'),
  setting('smtp.from-name', 'Java Jives'),
  setting('smtp.host', 'mail.novotrade.gr'),
  // Write-only: the backend sends the redaction marker, never the value.
  setting('smtp.password', '********', { secret: true }),
  setting('smtp.port', '465'),
  setting('smtp.reply-to', 'kostas@novotrade.gr'),
  setting('smtp.transport-security', 'IMPLICIT_TLS'),
  setting('smtp.username', 'erp@novotrade.gr'),
]

let me: Me = owner
let body: SettingView[] = settings

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/settings', () => HttpResponse.json({ items: body })),
  http.put('http://localhost/api/settings/:key', () => new HttpResponse(null, { status: 204 })),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  body = settings
  requests.reset()
})
afterAll(() => server.close())

function renderPage(page: () => React.ReactElement) {
  return render(
    <AppQueryProvider>
      <MemoryRouter>{page()}</MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the settings screens', () => {
  it('sends no write merely by rendering', async () => {
    renderPage(() => <EmailSettings />)
    await screen.findByText('mail.novotrade.gr')
    requests.expectNoWrites()
  })

  it('shows each page only its own keys', async () => {
    renderPage(() => <RetentionSettings />)
    await screen.findByText('FOREVER')

    // The two retention keys, and nothing from the other twelve.
    expect(screen.getByText('Keep sent messages for')).toBeInTheDocument()
    expect(screen.queryByText('SMTP server')).not.toBeInTheDocument()
    expect(screen.queryByText('Rounding threshold')).not.toBeInTheDocument()
  })

  it('renders the statutory cash limit with no edit control at all', async () => {
    renderPage(() => <DocumentSettings />)
    await screen.findByText('500.00')

    /*
     * ⚠️ The assertion that matters, and it is an ABSENCE.
     *
     * Every other row on this page has an "Edit <field>" button. This one has none — not a disabled
     * one, which is the tempting middle ground and is worse: it tells an administrator to go looking
     * for the grant that unlocks it, and there is none on any installation. The value is shown,
     * because the administrator reviewing configuration needs it.
     */
    expect(screen.queryByRole('button', { name: 'Edit Cash payment limit' })).not.toBeInTheDocument()
    expect(screen.getByText('Statutory')).toBeInTheDocument()

    // And the neighbouring writable rows DO have one — otherwise this test would pass on a page
    // that rendered no buttons at all.
    expect(screen.getByRole('button', { name: 'Edit Rounding threshold' })).toBeInTheDocument()
  })

  it('never displays the SMTP password, and says whether one is set', async () => {
    renderPage(() => <EmailSettings />)
    await screen.findByText('mail.novotrade.gr')

    expect(screen.getByText('Configured')).toBeInTheDocument()
    // Not the redaction marker either: it is the backend's placeholder, not something to show.
    expect(screen.queryByText('********')).not.toBeInTheDocument()
  })

  it('distinguishes a password never configured from one that is', async () => {
    // An unset key comes back with an empty value and no timestamps, which is the only signal.
    body = settings.map((entry) =>
      entry.key === 'smtp.password' ? { key: 'smtp.password', value: '', secret: true } : entry,
    )
    renderPage(() => <EmailSettings />)
    await screen.findByText('mail.novotrade.gr')

    expect(screen.getByText('Not configured')).toBeInTheDocument()
  })

  it('saves against the enum constant, not the dotted key', async () => {
    const user = userEvent.setup()
    renderPage(() => <DocumentSettings />)
    await screen.findByText('0.03')

    await user.click(screen.getByRole('button', { name: 'Edit Rounding threshold' }))
    const input = screen.getByDisplayValue('0.03')
    await user.clear(input)
    await user.type(input, '0.05')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    /*
     * ⚠️ `{key}` binds to `@PathVariable SettingsCatalog`, and no converter is registered — so the
     * path segment is the ENUM CONSTANT. Sending the dotted key is refused by Spring before any of
     * our code runs, and the response body's own `key` field is the dotted one, which is exactly
     * how somebody comes to use the wrong spelling.
     */
    await waitFor(() => {
      expect(requests.writes()).toContainEqual(
        expect.objectContaining({
          method: 'PUT',
          path: '/api/settings/LEDGER_ROUNDING_THRESHOLD',
        }),
      )
    })
  })

  it('offers only transport security values the backend accepts', async () => {
    const user = userEvent.setup()
    renderPage(() => <EmailSettings />)
    await screen.findByText('mail.novotrade.gr')

    await user.click(screen.getByRole('button', { name: 'Edit Transport security' }))
    await user.click(screen.getByRole('combobox'))

    expect(await screen.findByRole('option', { name: 'Implicit TLS (port 465)' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'STARTTLS (port 587)' })).toBeInTheDocument()
    // ⚠️ `SettingType`'s javadoc said `TLS`, which is not a constant. Offering it would produce a
    // 422 on every save.
    expect(screen.queryByRole('option', { name: /^TLS/ })).not.toBeInTheDocument()
  })

  it('shows the backend own description for a key rather than a translated copy', async () => {
    renderPage(() => <RetentionSettings />)
    expect(await screen.findByText('What email.retention.message-days does.')).toBeInTheDocument()
  })
})

describe('a role that may not read settings', () => {
  it('says what the server said rather than rendering an empty page', async () => {
    // ⚠️ SETTINGS is default-deny and no role holds it by grant — Owner and Admin reach it through
    // fullAccess — so a 403 here is an ordinary outcome, not a surprise.
    me = aUser({
      id: 9,
      role: { id: 6, name: 'SHOP', fullAccess: false, systemRole: false },
      sections: [{ section: Section.PRODUCTS, level: AccessLevel.VIEW, available: true }],
    })
    server.use(
      http.get('http://localhost/api/settings', () => new HttpResponse(null, { status: 403 })),
    )

    renderPage(() => <DocumentSettings />)
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
    requests.expectNoWrites()
  })
})
