import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { useSettingsControllerPut } from '@/api/generated/endpoints/settings/settings'
import type { SettingView } from '@/api/generated/model'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import {
  RETENTION_FOREVER,
  ROUNDING_MODE_VALUES,
  TRANSPORT_SECURITY_VALUES,
  type SettingSpec,
} from './settings-catalogue'

/**
 * One catalogued setting, read and — where the backend allows it — written.
 *
 * Built on `FieldEditor` rather than beside it, so a setting behaves exactly like a field on a
 * product or a customer: one request per field, the refusal shown against the field that caused it,
 * and no batching that could half-apply.
 *
 * **The row's description is the server's own text.** Every catalogued key carries one, written for
 * this screen — the `cash.payment.limit` row explains a statute and the `smtp.transport-security`
 * row explains why a boolean would not do. Translating them here would mean maintaining a second
 * copy of an argument, so the short label is translated and the explanation is not.
 */

/** Three states that must not be collapsed: writable, statutory, and write-only. */
export function SettingRow({ spec, setting }: { spec: SettingSpec; setting?: SettingView }) {
  const { t } = useTranslation('common')
  const queryClient = useQueryClient()
  const put = useSettingsControllerPut()

  const save = async (value: string) => {
    // ⚠️ The path segment is the ENUM CONSTANT, not the dotted key the row displays.
    await put.mutateAsync({ key: spec.constant, data: { value } })
    await queryClient.invalidateQueries({ queryKey: ['/api/settings'] })
  }

  const description = setting?.description
  const label = t(`settings.key.${spec.key}`, { defaultValue: spec.key })

  return (
    <div>
      <SettingControl spec={spec} setting={setting} label={label} onSave={save} />
      {description !== undefined && (
        <p className="text-muted-foreground pl-52 text-xs">{description}</p>
      )}
    </div>
  )
}

function SettingControl({
  spec,
  setting,
  label,
  onSave,
}: {
  spec: SettingSpec
  setting?: SettingView
  label: string
  onSave: (value: string) => Promise<unknown>
}) {
  const { t } = useTranslation('common')
  const value = setting?.value ?? ''

  /*
   * ⚠️ Statutory, and therefore NO edit affordance at all — not a disabled one.
   *
   * `FieldEditor`'s two unavailable states are `editable: false` ("not yours to edit", no control)
   * and `lockedReason` ("editable in general, fixed on this record", disabled control with the
   * reason). Neither fits: this is not about the role, and it is not fixed on one record among many
   * — the setting has no write route on any installation. A disabled control would invite an
   * administrator to hunt for the permission that unlocks it, and there is none. So it renders as
   * plain text with the reason beside it, which is the same shape `RoleDetail` uses for a
   * description that has no PATCH route.
   */
  if (spec.readOnlyReason !== undefined) {
    return (
      <div className="border-b py-2">
        <div className="flex items-baseline justify-between gap-4">
          <Label className="text-muted-foreground w-48 shrink-0 text-sm">{label}</Label>
          <div className="flex flex-1 items-baseline gap-2">
            <span className="flex-1 text-sm">{value === '' ? <UnsetValue /> : value}</span>
            <Badge variant="secondary">{t(`settings.${spec.readOnlyReason}`)}</Badge>
          </div>
        </div>
        <p className="text-muted-foreground mt-1 pl-52 text-sm">
          {t(`settings.${spec.readOnlyReason}Reason`)}
        </p>
      </div>
    )
  }

  /*
   * Write-only. The backend never returns the value — not even redacted-with-a-length — so there is
   * nothing to display and "show it again" is not a feature that was left out: it cannot exist.
   * What the screen CAN say is whether one is configured, and the two states are distinguishable:
   * a key with no row comes back with an empty value and no timestamps.
   */
  if (spec.writeOnly === true) {
    const configured = value !== ''
    return (
      <FieldEditor<string>
        label={label}
        value=""
        display={
          configured ? (
            <span className="flex items-center gap-2 text-sm">
              <Badge variant="secondary">{t('settings.configured')}</Badge>
              {setting?.updatedAt !== undefined && (
                <span className="text-muted-foreground text-xs">
                  {t('settings.updatedAt', { at: new Date(setting.updatedAt).toLocaleString() })}
                </span>
              )}
            </span>
          ) : (
            <Badge variant="outline">{t('settings.notConfigured')}</Badge>
          )
        }
        onSave={onSave}
        editable
        isValid={(draft) => draft.trim() !== ''}
      >
        {(draft, setDraft) => (
          <Input
            type="password"
            value={draft}
            autoComplete="new-password"
            placeholder={t('settings.newValuePlaceholder')}
            onChange={(event) => setDraft(event.target.value)}
          />
        )}
      </FieldEditor>
    )
  }

  return (
    <FieldEditor<string>
      label={label}
      value={value}
      display={value === '' ? <UnsetValue /> : value}
      onSave={onSave}
      editable
      isValid={(draft) => isValidFor(spec, draft)}
    >
      {(draft, setDraft) => <Editor spec={spec} draft={draft} setDraft={setDraft} />}
    </FieldEditor>
  )
}

function Editor({
  spec,
  draft,
  setDraft,
}: {
  spec: SettingSpec
  draft: string
  setDraft: (value: string) => void
}) {
  const { t } = useTranslation('common')

  if (spec.kind === 'ROUNDING_MODE' || spec.kind === 'TRANSPORT_SECURITY') {
    const values =
      spec.kind === 'ROUNDING_MODE' ? ROUNDING_MODE_VALUES : TRANSPORT_SECURITY_VALUES
    return (
      <OptionSelect
        value={draft === '' ? null : draft}
        onValueChange={(chosen) => setDraft(chosen ?? '')}
        options={values.map((constant) => ({
          value: constant,
          label: t(`settings.value.${constant}`, { defaultValue: constant }),
        }))}
      />
    )
  }

  /*
   * ⚠️ `<input type="number">` is banned by an ESLint rule and these are text inputs deliberately.
   * None of these is money — `ledger.rounding.threshold` is an amount but travels as an opaque
   * setting string, not as a `Money` — so the decimal components do not apply either. What matters
   * is that the value reaches the server exactly as typed: the backend validates it and refuses a
   * bad one with a message naming what it expected, which is a better answer than a browser widget
   * silently reshaping "0,03" into something else.
   */
  return (
    <Input
      value={draft}
      inputMode={spec.kind === 'POSITIVE_INTEGER' ? 'numeric' : undefined}
      onChange={(event) => setDraft(event.target.value)}
      placeholder={spec.kind === 'RETENTION_DAYS' ? RETENTION_FOREVER : undefined}
    />
  )
}

/**
 * Refuses a save that cannot succeed.
 *
 * ⚠️ **Deliberately thin, and it is not a copy of the backend's validation.** The server validates
 * every value before storing it, so a refused write leaves the previous one intact and answers with
 * a message naming what it expected. This only stops the obviously empty case — a blank value is
 * refused with a `400` that says nothing useful about which key was blank. Everything else is the
 * server's to judge, and `Refusal` shows what it said.
 */
function isValidFor(spec: SettingSpec, draft: string): boolean {
  const trimmed = draft.trim()
  if (trimmed === '') return false
  if (spec.kind === 'RETENTION_DAYS') {
    return trimmed.toUpperCase() === RETENTION_FOREVER || /^\d+$/.test(trimmed)
  }
  if (spec.kind === 'POSITIVE_INTEGER') return /^\d+$/.test(trimmed)
  return true
}
