import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { CheckIcon, WarningCircleIcon } from '@/components/icons'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Handing a password to a person — the only way this application shows one.
 *
 * **Generated, shown once, acknowledged, gone.** The decision, taken before this was built: no
 * confirm-field, because there is nothing to confirm — the operator did not choose the value and
 * cannot mistype it. What can go wrong is the other thing entirely: closing the dialog without
 * having taken the password, at which point nobody can log in and the only remedy is another reset.
 * So the close is gated on an explicit acknowledgment rather than on retyping.
 *
 * **It is never retrievable.** The value lives in one component's state, is not a query key, is not
 * written to the query cache, is not in a URL, and is not sent anywhere except the one request that
 * sets it. `UserView` has no password field and the backend has no route that returns one — see
 * `UserController`'s "No password ever leaves through here" — so "show it again" is not a feature
 * that was left out, it is one that cannot exist. That is why the acknowledgment is worth insisting
 * on: this dialog really is the only chance.
 *
 * **The dialog cannot be dismissed by clicking away or pressing Escape once the password is
 * active.** Both are how a modal gets closed by accident, and an accident here costs a password.
 *
 * **There is no `open` prop, deliberately.** A caller renders this only while a hand-off is in
 * progress, so the acknowledgment and the copied flag live and die with one password — a component
 * that stayed mounted between hand-offs could open the second one already acknowledged.
 */

export interface PasswordHandoffProps {
  /** The generated value. Held by the caller so this component owns no credential of its own. */
  password: string
  /** Whose it is. A dialog left open on a shared machine still has to say who it belongs to. */
  username: string
  /** True once the backend has accepted it — before that, abandoning costs nothing. */
  applied: boolean
  /** Sends the request. Absent when the password was set as part of creating the account. */
  onApply?: () => void
  applying?: boolean
  /** Whatever the request threw. Rendered by the shared `Refusal`, like every other write. */
  error?: unknown
  /** Abandon before it has been applied. */
  onCancel?: () => void
  /** The operator has acknowledged taking it. The only way out once it is active. */
  onDone: () => void
}

export function PasswordHandoff({
  password,
  username,
  applied,
  onApply,
  applying = false,
  error,
  onCancel,
  onDone,
}: PasswordHandoffProps) {
  const { t } = useTranslation('common')
  const [acknowledged, setAcknowledged] = useState(false)
  const [copied, setCopied] = useState(false)

  const copy = () => {
    /*
     * `navigator.clipboard` is absent over plain HTTP and in some embedded browsers, and a copy
     * button that silently does nothing is exactly how a password gets lost. The value is also in
     * a selectable input above, which is the fallback that always works — so this reports success
     * only when it actually succeeded.
     */
    void navigator.clipboard
      ?.writeText(password)
      .then(() => setCopied(true))
      .catch(() => setCopied(false))
  }

  return (
    <Dialog
      open
      // Never closes itself. Every exit goes through a button below, so an outside click or an
      // Escape keypress cannot discard a password nobody has written down.
      disablePointerDismissal
      onOpenChange={() => {}}
    >
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>
            {applied ? t('users.password.activeTitle') : t('users.password.title')}
          </DialogTitle>
          <DialogDescription>
            {applied
              ? t('users.password.activeBody', { username })
              : t('users.password.body', { username })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="generated-password">{t('users.password.label')}</Label>
          <div className="flex items-center gap-2">
            <Input
              id="generated-password"
              readOnly
              value={password}
              className="font-mono tracking-wide"
              onFocus={(event) => event.currentTarget.select()}
            />
            <Button type="button" variant="outline" size="sm" onClick={copy}>
              {copied ? t('users.password.copied') : t('users.password.copy')}
            </Button>
          </div>
          <p className="text-muted-foreground flex items-start gap-1 text-sm">
            <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
            {t('users.password.shownOnce')}
          </p>
          {/* The backend's behaviour, stated before the fact rather than discovered afterwards:
              `UserServiceImpl.changePassword` ends every session that account has open. */}
          <p className="text-muted-foreground text-sm">{t('users.password.endsSessions')}</p>
        </div>

        <Refusal error={error} />

        {applied ? (
          <>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-1"
                checked={acknowledged}
                onChange={(event) => setAcknowledged(event.target.checked)}
              />
              {t('users.password.acknowledge', { username })}
            </label>
            <DialogFooter>
              <Button type="button" disabled={!acknowledged} onClick={onDone}>
                <CheckIcon aria-hidden /> {t('users.password.done')}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <DialogFooter>
            {onCancel && (
              <Button type="button" variant="ghost" onClick={onCancel} disabled={applying}>
                {t('field.cancel')}
              </Button>
            )}
            {onApply && (
              <Button type="button" onClick={onApply} disabled={applying}>
                {applying ? t('users.password.setting') : t('users.password.set')}
              </Button>
            )}
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}
