import { useTranslation } from 'react-i18next'

import { useSettingsControllerSettings } from '@/api/generated/endpoints/settings/settings'
import { Refusal } from '@/components/refusal'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { unwrapList } from '@/components/data-table/list-response'

import { SettingRow } from './setting-row'
import {
  DOCUMENT_SETTINGS,
  EMAIL_SETTINGS,
  RETENTION_SETTINGS,
  type SettingSpec,
} from './settings-catalogue'

/**
 * The settings screens — three pages over one endpoint.
 *
 * `GET /api/settings` returns the whole catalogue in one response, so each page fetches the same
 * query and renders its own slice of it. That is deliberate: three pages issuing three identical
 * requests would be three cache entries of the same data, and a save on one page would leave the
 * other two stale.
 *
 * ⚠️ **There is no "General" page.** The nav declared one and the 18 catalogued keys distribute
 * across these three with nothing left for it, so it was removed rather than shipped empty — the
 * permanent placeholder step 16b existed to delete.
 *
 * ⚠️ **`SETTINGS` is default-deny and no role holds it by grant** — Owner and Admin reach these
 * through `fullAccess`. A 403 is therefore an ordinary outcome here, not an error state to be
 * surprised by, and `Refusal` says what the server said rather than rendering an empty page.
 */
function SettingsGroup({ titleKey, specs }: { titleKey: string; specs: readonly SettingSpec[] }) {
  const { t } = useTranslation('common')
  const query = useSettingsControllerSettings()

  // Keyed by the DOTTED key, which is what the response body carries — the enum constant is only
  // ever a path segment. Both spellings live in `settings-catalogue.ts` for exactly this reason.
  const byKey = new Map(
    unwrapList(query.data).rows.map((setting) => [setting.key ?? '', setting] as const),
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t(titleKey)}</CardTitle>
      </CardHeader>
      <CardContent>
        <Refusal error={query.error} className="mb-3" />
        {query.isLoading ? (
          <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
        ) : (
          <div className="space-y-1">
            {specs.map((spec) => (
              <SettingRow key={spec.key} spec={spec} setting={byKey.get(spec.key)} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function DocumentSettings() {
  return <SettingsGroup titleKey="settings.documentsTitle" specs={DOCUMENT_SETTINGS} />
}

export function EmailSettings() {
  return <SettingsGroup titleKey="settings.emailTitle" specs={EMAIL_SETTINGS} />
}

export function RetentionSettings() {
  return <SettingsGroup titleKey="settings.retentionTitle" specs={RETENTION_SETTINGS} />
}
