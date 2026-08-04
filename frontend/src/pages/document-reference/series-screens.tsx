import type { TFunction } from 'i18next'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import type { SalesChannel } from '@/api/generated/model'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { SegmentedControl } from '@/components/segmented-control'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import {
  NO_CHANNEL,
  NO_TARGET,
  channelLabel,
  channelOptions,
  idOf,
  sortCodeOf,
  type NewSeriesBody,
  type SeriesView,
} from './values'

/**
 * Sales and purchase document series share these screens, with **one** difference: channel.
 *
 * <h2>⚠️ Channel is on the sales series ONLY, and its absence on the purchase side is a decision</h2>
 *
 * `purchase_document_series` has **no channel column**, no channel route and no channel component —
 * because channel is where a *sale* came from and never applies to a purchase. A nullable column
 * that could only ever be null invites somebody to fill it, and a purchase series carrying
 * `ECOMMERCE` would be storable, meaningless and indistinguishable from data. `showChannel` carries
 * that difference here, and there is a test asserting the purchase screen renders no such control —
 * "there is no route" and "the route silently does nothing" look identical to an operator.
 *
 * <h2>⚠️ A sales invoice's channel comes FROM its series</h2>
 *
 * Since R1b, `NewSalesInvoice` has no `channel` component at all: ΑΛΠW is the web series, so an
 * invoice in it is a web sale *by definition* rather than by somebody remembering to tick a box.
 * That is why this field matters more than it looks, and why the screen says so in as many words
 * rather than leaving it to be discovered at F5.
 *
 * <h2>⚠️ Null channel is a NAMED option, never a blank</h2>
 *
 * Six of the owner's series — ΠΡΦ, ΔΠΠ, ΔΑ, ΣΑΥΤ, ΠΣΑΥΤ — are channel-less, so this is a normal
 * case rather than an edge one. `PUT …/channel` takes a `@Mandatory` channel, so choosing "not a
 * sales channel" is a **`DELETE …/channel`** rather than a `PUT` of null: the resource being removed
 * is the mapping itself, and a body carrying only null says nothing a caller can be held to.
 */


// ================================================================================================
// Detail
// ================================================================================================

export interface SeriesDetailProps {
  t: TFunction
  series: SeriesView
  editable: boolean
  showChannel: boolean
  backTo: string
  backLabel: string
  /** Every series in the same table — for the transformation target's label and its options. */
  siblings: readonly SeriesView[]
  /** Every document type in the same table, for repointing the series. */
  documentTypes: readonly { id: number; description: string }[]
  onDescribe: (description: string) => Promise<unknown>
  onAbbreviation: (abbreviation: string) => Promise<unknown>
  onSortCode: (sortCode: number) => Promise<unknown>
  onDocumentType: (documentTypeId: number) => Promise<unknown>
  onGetsMark: (getsMark: boolean) => Promise<unknown>
  /** `null` sends the DELETE. Absent on the purchase screen, which has no channel at all. */
  onChannel?: (channel: SalesChannel | null) => Promise<unknown>
  onTransformationTarget: (targetSeriesId: number | null) => Promise<unknown>
  onActivate: () => void
  onDeactivate: () => void
  activationError: unknown
  activationPending: boolean
}

export function SeriesDetail({
  t,
  series,
  editable,
  showChannel,
  backTo,
  backLabel,
  siblings,
  documentTypes,
  onDescribe,
  onAbbreviation,
  onDocumentType,
  onGetsMark,
  onChannel,
  onTransformationTarget,
  onActivate,
  onDeactivate,
  activationError,
  activationPending,
}: SeriesDetailProps) {
  /*
   * ⚠️ THE R2 FREEZE, AND IT IS `lockedReason` RATHER THAN THE PLAIN-TEXT TREATMENT.
   *
   * Before R2 these three fields had no write route on any installation, which is
   * `frontend/README.md`'s THIRD state — plain text with the reason, no control at all. They now
   * have one, and it works right up until a document is recorded in the series. That makes them
   * the SECOND state exactly: "editable in general, fixed on THIS record" — shown, disabled, with
   * the reason. The distinction is the whole point of the backend sub-part R2 grew.
   *
   * The reason is composed here rather than sent by the server, deliberately: every existing
   * `lockedReason` in this application is a client i18n string, and the backend localises nothing
   * (Q47(b)), so a server sentence would render as English prose beside Greek labels. The server's
   * own fuller refusal still arrives through `Refusal` if a request is ever sent.
   */
  const frozen = series.inUse ? t('docSeries.locked.inUse') : undefined

  const typeOptions = documentTypes.map((type) => ({
    value: String(type.id),
    label: type.description,
  }))

  // ⚠️ The row being edited is omitted from its own options — belt. The server refuses a
  // self-reference too (422, and a CHECK constraint under that) — braces.
  const targetOptions = [
    { value: NO_TARGET, label: t('docSeries.transform.none') },
    ...siblings
      .filter((sibling) => sibling.id !== series.id)
      .map((sibling) => ({
        value: String(sibling.id),
        label: `${sibling.abbreviation} — ${sibling.description}`,
      })),
  ]

  const targetLabel =
    series.transformableIntoSeriesId === undefined
      ? t('docSeries.transform.none')
      : (siblings.find((sibling) => sibling.id === series.transformableIntoSeriesId)
          ?.abbreviation ?? `#${series.transformableIntoSeriesId}`)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to={backTo} className="text-muted-foreground text-sm hover:underline">
            ← {backLabel}
          </Link>
          <h1 className="text-lg font-semibold">{series.abbreviation}</h1>
          {series.inUse && <Badge variant="outline">{t('docSeries.flag.inUse')}</Badge>}
          {series.active === false && (
            <Badge variant="outline">{t('docSeries.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (series.active === false ? (
            <Button size="sm" variant="outline" disabled={activationPending} onClick={onActivate}>
              {t('docSeries.activate')}
            </Button>
          ) : (
            <Button size="sm" variant="outline" disabled={activationPending} onClick={onDeactivate}>
              {t('docSeries.deactivate')}
            </Button>
          ))}
      </div>

      <Refusal error={activationError} />

      <Card>
        <CardHeader>
          <CardTitle>{t('docSeries.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <FieldEditor<string>
            label={t('docSeries.column.abbreviation')}
            value={series.abbreviation ?? ''}
            display={series.abbreviation ?? <UnsetValue />}
            editable={editable}
            {...(frozen === undefined ? {} : { lockedReason: frozen })}
            isValid={(value) => value.trim() !== ''}
            onSave={(value) => onAbbreviation(value.trim())}
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('docSeries.column.description')}
            value={series.description ?? ''}
            display={series.description ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={(value) => onDescribe(value.trim())}
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('docSeries.column.documentType')}
            value={String(series.documentTypeId)}
            display={series.documentTypeDescription ?? <UnsetValue />}
            editable={editable}
            {...(frozen === undefined ? {} : { lockedReason: frozen })}
            onSave={(value) => onDocumentType(idOf(value))}
          >
            {(value, setValue) => (
              <OptionSelect
                aria-label={t('docSeries.column.documentType')}
                options={typeOptions}
                value={value}
                onValueChange={(next) => setValue(next ?? value)}
              />
            )}
          </FieldEditor>

          {showChannel && onChannel && (
            <>
              <FieldEditor<string>
                label={t('docSeries.column.channel')}
                value={series.channel ?? NO_CHANNEL}
                display={channelLabel(t, series.channel)}
                editable={editable}
                onSave={(value) =>
                  // ⚠️ "Not a sales channel" is a DELETE, not a PUT of null. The route's body
                  // component is @Mandatory; the resource being removed is the mapping itself.
                  onChannel(value === NO_CHANNEL ? null : (value as SalesChannel))
                }
              >
                {(value, setValue) => (
                  <OptionSelect
                    aria-label={t('docSeries.column.channel')}
                    options={channelOptions(t)}
                    value={value}
                    onValueChange={(next) => setValue(next ?? value)}
                  />
                )}
              </FieldEditor>
              <p className="text-muted-foreground pb-2 text-sm">{t('docSeries.channel.help')}</p>
            </>
          )}

          <FieldEditor<boolean>
            label={t('docSeries.column.getsMark')}
            value={series.getsMark ?? false}
            display={series.getsMark ? t('docSeries.yes') : t('docSeries.no')}
            editable={editable}
            {...(frozen === undefined ? {} : { lockedReason: frozen })}
            onSave={(value) => onGetsMark(value)}
          >
            {(value, setValue) => (
              <SegmentedControl
                aria-label={t('docSeries.column.getsMark')}
                options={[
                  { value: 'yes', label: t('docSeries.yes') },
                  { value: 'no', label: t('docSeries.no') },
                ]}
                value={value ? 'yes' : 'no'}
                onValueChange={(next) => setValue(next === 'yes')}
              />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('docSeries.column.transformableInto')}
            value={
              series.transformableIntoSeriesId === undefined
                ? NO_TARGET
                : String(series.transformableIntoSeriesId)
            }
            display={targetLabel}
            editable={editable}
            onSave={(value) => onTransformationTarget(value === NO_TARGET ? null : idOf(value))}
          >
            {(value, setValue) => (
              <OptionSelect
                aria-label={t('docSeries.column.transformableInto')}
                options={targetOptions}
                value={value}
                onValueChange={(next) => setValue(next ?? value)}
              />
            )}
          </FieldEditor>
        </CardContent>
      </Card>
    </div>
  )
}


// ================================================================================================
// Create
// ================================================================================================

export function SeriesCreateForm({
  t,
  showChannel,
  titleKey,
  backTo,
  backLabel,
  documentTypes,
  siblings,
  onSubmit,
  error,
  pending,
}: {
  t: TFunction
  showChannel: boolean
  titleKey: string
  backTo: string
  backLabel: string
  documentTypes: readonly { id: number; description: string }[]
  siblings: readonly SeriesView[]
  onSubmit: (body: NewSeriesBody) => void
  error: unknown
  pending: boolean
}) {
  const [abbreviation, setAbbreviation] = useState('')
  const [sortCode, setSortCode] = useState('')
  const [description, setDescription] = useState('')
  const [documentTypeId, setDocumentTypeId] = useState<string | null>(null)
  /** ⚠️ Starts at the NAMED "not a sales channel" option, never at a blank. */
  const [channel, setChannel] = useState<string>(NO_CHANNEL)
  const [getsMark, setGetsMark] = useState<'yes' | 'no' | ''>('')
  const [target, setTarget] = useState<string>(NO_TARGET)

  const complete =
    abbreviation.trim() !== '' &&
    description.trim() !== '' &&
    documentTypeId !== null &&
    getsMark !== ''

  const submit = (event: FormEvent) => {
    event.preventDefault()
    // `complete` already narrows both away from their unanswered states.
    if (!complete || documentTypeId === null) return

    onSubmit({
      abbreviation: abbreviation.trim(),
      description: description.trim(),
      sortCode: sortCodeOf(sortCode),
      documentTypeId: idOf(documentTypeId),
      // Omitted, not null — a series with no channel is a real configuration and the request
      // record's component is nullable for exactly that reason.
      ...(showChannel && channel !== NO_CHANNEL ? { channel: channel as SalesChannel } : {}),
      getsMark: getsMark === 'yes',
      ...(target === NO_TARGET ? {} : { transformableIntoSeriesId: idOf(target) }),
    })
  }

  return (
    <div className="max-w-2xl space-y-4">
      <Link to={backTo} className="text-muted-foreground text-sm hover:underline">
        ← {backLabel}
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{t(titleKey)}</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-1">
              <Label htmlFor="abbreviation">{t('docSeries.column.abbreviation')}</Label>
              <p className="text-muted-foreground text-sm">{t('docSeries.abbreviation.help')}</p>
              <Input
                id="abbreviation"
                value={abbreviation}
                onChange={(event) => setAbbreviation(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="description">{t('docSeries.column.description')}</Label>
              <Input
                id="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="sortCode">{t('docSeries.column.sortCode')}</Label>
              <p className="text-muted-foreground text-sm">{t('docSeries.sortCode.help')}</p>
              {/* Digits only — see the document-type form for why not `type="number"`. */}
              <Input
                id="sortCode"
                inputMode="numeric"
                value={sortCode}
                onChange={(event) => setSortCode(event.target.value.replace(/[^0-9]/g, ''))}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="documentType">{t('docSeries.column.documentType')}</Label>
              <OptionSelect
                id="documentType"
                aria-label={t('docSeries.column.documentType')}
                options={documentTypes.map((type) => ({
                  value: String(type.id),
                  label: type.description,
                }))}
                value={documentTypeId}
                onValueChange={setDocumentTypeId}
              />
            </div>

            {showChannel && (
              <div className="space-y-1">
                <Label htmlFor="channel">{t('docSeries.column.channel')}</Label>
                <p className="text-muted-foreground text-sm">{t('docSeries.channel.help')}</p>
                <OptionSelect
                  id="channel"
                  aria-label={t('docSeries.column.channel')}
                  options={channelOptions(t)}
                  value={channel}
                  onValueChange={(next) => setChannel(next ?? NO_CHANNEL)}
                />
              </div>
            )}

            <div className="space-y-1">
              <Label htmlFor="getsMark">{t('docSeries.column.getsMark')}</Label>
              <p className="text-muted-foreground text-sm">{t('docSeries.getsMark.help')}</p>
              <SegmentedControl
                aria-label={t('docSeries.column.getsMark')}
                options={[
                  { value: 'yes', label: t('docSeries.yes') },
                  { value: 'no', label: t('docSeries.no') },
                ]}
                value={getsMark}
                onValueChange={(next) => setGetsMark(next === 'yes' ? 'yes' : 'no')}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="target">{t('docSeries.column.transformableInto')}</Label>
              <OptionSelect
                id="target"
                aria-label={t('docSeries.column.transformableInto')}
                options={[
                  { value: NO_TARGET, label: t('docSeries.transform.none') },
                  ...siblings.map((sibling) => ({
                    value: String(sibling.id),
                    label: `${sibling.abbreviation} — ${sibling.description}`,
                  })),
                ]}
                value={target}
                onValueChange={(next) => setTarget(next ?? NO_TARGET)}
              />
            </div>

            <p className="text-muted-foreground text-sm">{t('docSeries.frozenAfterUse')}</p>

            <Refusal error={error} />

            <Button type="submit" disabled={!complete || pending}>
              {pending ? t('docSeries.creating') : t('docSeries.save')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
