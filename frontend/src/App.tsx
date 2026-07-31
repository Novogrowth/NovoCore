import { useTranslation } from 'react-i18next'

import { useLogout, useSession, useSessionExpiryHandler } from '@/auth/session'
import { AppSidebar } from '@/components/app-sidebar'
import { OptionSelect } from '@/components/option-select'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { LANGUAGES, resolveLanguage, type Language } from '@/i18n'
import { useChangeLanguage, useLanguageSync } from '@/i18n/useLanguage'
import { LoginPage } from '@/pages/login'
import { AppRoutes } from '@/routes'

/**
 * The shell: who is signed in, what language they read, and everything else inside it.
 *
 * There is exactly one decision here — signed in or not — and it is made from `/api/me` rather
 * than from anything this application remembers for itself.
 */
export default function App() {
  const { me, isLoading, isSignedOut, error } = useSession()
  const { t } = useTranslation('common')

  useSessionExpiryHandler()
  useLanguageSync()

  if (isLoading) {
    return (
      <div className="text-muted-foreground flex min-h-screen items-center justify-center text-sm">
        {t('app.loading')}
      </div>
    )
  }

  /*
   * The server could not be reached, or failed. This is deliberately NOT the login form: a
   * password box in front of an unreachable server invites somebody to type a credential that
   * cannot be checked, and presents an outage as a sign-out.
   */
  if (error) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-2 p-6 text-center">
        <p className="font-medium">{t('app.unreachable.title')}</p>
        <p className="text-muted-foreground text-sm">{t('app.unreachable.body')}</p>
      </div>
    )
  }

  if (isSignedOut || !me) return <LoginPage />

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 h-4" />
          <span className="text-sm font-medium">{me.displayName ?? me.username}</span>
          <div className="ml-auto flex items-center gap-2">
            <LanguageSelect current={resolveLanguage(me.language)} />
            <SignOutButton />
          </div>
        </header>
        <main className="flex flex-1 flex-col gap-4 p-4">
          <AppRoutes />
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}

/** Language is stored on the account, so choosing it here follows the person to another machine. */
function LanguageSelect({ current }: { current: Language }) {
  const { t } = useTranslation('common')
  const changeLanguage = useChangeLanguage()

  return (
    <OptionSelect
      className="w-36"
      aria-label={t('language.label')}
      options={LANGUAGES.map((language) => ({
        value: language,
        label: t(`language.${language}`),
      }))}
      value={current}
      onValueChange={(value) => changeLanguage.mutate(value as Language)}
      disabled={changeLanguage.isPending}
    />
  )
}

function SignOutButton() {
  const { t } = useTranslation('common')
  const logout = useLogout()

  return (
    <Button variant="outline" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
      {t('auth.signOut')}
    </Button>
  )
}
