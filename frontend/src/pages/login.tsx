import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { ApiError } from '@/api/http'
import { useLogin } from '@/auth/session'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Signing in.
 *
 * `POST /login` is Spring Security's own endpoint: form-encoded, 204 on success, 401 on failure,
 * with the session cookie set on the response. The CSRF token it needs is already in the browser
 * — the cookie is written on the first response, including the 401 that brought us here.
 *
 * A 401 says only that the combination was not accepted. It does not say which half was wrong,
 * because a form that distinguishes them is a form that confirms usernames.
 */
export function LoginPage() {
  const { t } = useTranslation('common')
  const login = useLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    login.mutate({ username, password })
  }

  const refused = login.error instanceof ApiError && login.error.isUnauthenticated

  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>{t('app.name')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">{t('auth.username')}</Label>
              <Input
                id="username"
                name="username"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">{t('auth.password')}</Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>

            {refused && (
              <p role="alert" className="text-destructive text-sm">
                {t('auth.failed')}
              </p>
            )}

            <Button type="submit" className="w-full" disabled={login.isPending}>
              {login.isPending ? t('auth.signingIn') : t('auth.signIn')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
