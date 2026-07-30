import { useTranslation } from 'react-i18next'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * What a route renders until its screen is built.
 *
 * Deliberately not a mock of the screen to come: it states that the foundations are in place and
 * names the endpoint the real screen will be built on, so a placeholder cannot be mistaken for a
 * half-working feature.
 */
export function ScreenPlaceholder({ titleKey, endpoint }: { titleKey: string; endpoint?: string }) {
  const { t } = useTranslation('nav')
  const { t: tc } = useTranslation('common')

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{t(titleKey)}</CardTitle>
      </CardHeader>
      <CardContent className="text-muted-foreground space-y-2 text-sm">
        <p>{endpoint ? tc('page.placeholder.body', { endpoint }) : tc('page.notBuilt.body')}</p>
      </CardContent>
    </Card>
  )
}

export function NotFound() {
  const { t } = useTranslation('common')
  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{t('page.notFound.title')}</CardTitle>
      </CardHeader>
      <CardContent className="text-muted-foreground text-sm">{t('page.notFound.body')}</CardContent>
    </Card>
  )
}

/**
 * What someone sees at a route their role does not include.
 *
 * It says the section is not available to them and does not say what is in it — the same
 * asymmetry the backend applies on purpose, where a validation refusal explains itself and a
 * permission refusal says nothing.
 */
export function NoAccess() {
  const { t } = useTranslation('common')
  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{t('page.noAccess.title')}</CardTitle>
      </CardHeader>
      <CardContent className="text-muted-foreground text-sm">{t('page.noAccess.body')}</CardContent>
    </Card>
  )
}
