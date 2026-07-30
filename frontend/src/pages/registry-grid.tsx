import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ADAPTERS, MODULES } from '@/nav/registry'

/**
 * The adapter and module grids.
 *
 * Every row is disabled and marked not-built, and **nothing is persisted**. There is no settings
 * key and no API behind these toggles, so a switch that appeared to work would silently forget
 * what it was told — which is worse than one that says plainly it does nothing. The day a toggle
 * API exists, this page gains a mutation and the registry stays exactly as it is.
 */
function Grid({
  title,
  body,
  entries,
  labelPrefix,
}: {
  title: string
  body: string
  entries: readonly { key: string; status: string }[]
  labelPrefix: 'adapter' | 'module'
}) {
  const { t } = useTranslation('common')

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground text-sm">{body}</p>
        <ul className="grid gap-2 sm:grid-cols-2">
          {entries.map((entry) => (
            <li
              key={entry.key}
              className="flex items-center justify-between rounded-md border px-3 py-2 opacity-60"
              aria-disabled
            >
              <span className="text-sm">{t(`${labelPrefix}.${entry.key}`)}</span>
              <Badge variant="secondary">{t('registry.notBuilt')}</Badge>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

export function AdaptersGrid() {
  const { t } = useTranslation('common')
  return (
    <Grid
      title={t('registry.adaptersTitle')}
      body={t('registry.adaptersBody')}
      entries={ADAPTERS}
      labelPrefix="adapter"
    />
  )
}

export function ModulesGrid() {
  const { t } = useTranslation('common')
  return (
    <Grid
      title={t('registry.modulesTitle')}
      body={t('registry.modulesBody')}
      entries={MODULES}
      labelPrefix="module"
    />
  )
}
