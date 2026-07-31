import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

import { ApiError } from '@/api/http'
import { WarningCircleIcon } from '@/components/icons'
import { cn } from '@/lib/utils'

/**
 * What the backend said when it refused, put on screen.
 *
 * **A refused write that shows nothing is indistinguishable from one that worked.** That is not a
 * hypothetical: deactivating a product that is a component of an active bundle is answered with a
 * 422 and a complete explanation — *"…is a component of active bundle(s) TEST-PRODUCT-KIT-01, which
 * could not be assembled without it. Dissolve or re-define the bundle first"* — and the screen
 * rendered nothing at all, so the button read as dead. The backend had done everything right; the
 * message was thrown away on this side.
 *
 * It lives here rather than inside `FieldEditor` because refusals are not a property of field
 * editing. Any write can be refused, and every one of them owes the operator the reason.
 *
 * **The message is the backend's own `detail`, and its absence is meaningful.** Step 14 made a
 * validation refusal explain itself and a permission refusal deliberately say nothing, so a 403
 * carries no detail by design — {@link refusalMessage} falls back rather than presenting the
 * silence as a fault. ⚠️ These messages arrive as English prose even in the Greek UI: the backend
 * localises nothing (Q47(b)) and there are no error codes to translate from.
 */

/**
 * Turns whatever a failed write threw into something worth showing an operator.
 *
 * Deliberately not exported: a call site that wants the text wants to render it, and the component
 * below is how. Exporting it invites a second place that renders a refusal its own way.
 */
function refusalMessage(error: unknown, t: TFunction): string {
  if (error instanceof ApiError) return error.detail ?? t('field.refused')
  // Not an ApiError at all — `fetch` throws a TypeError when the server cannot be reached, and
  // reporting that as a refusal would tell the operator their change was rejected when it was
  // never delivered.
  return t('field.unreachable')
}

/** Renders nothing when there is no error, so a call site needs no conditional of its own. */
export function Refusal({ error, className }: { error: unknown; className?: string }) {
  const { t } = useTranslation('common')
  if (!error) return null

  return (
    <p
      role="alert"
      className={cn('text-destructive flex items-start gap-1 text-sm', className)}
    >
      <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
      {refusalMessage(error, t)}
    </p>
  )
}
