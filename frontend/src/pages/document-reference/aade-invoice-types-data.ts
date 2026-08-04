import type { TFunction } from 'i18next'

import { useAadeInvoiceTypeControllerInvoiceTypes } from '@/api/generated/endpoints/aade-invoice-type/aade-invoice-type'
import {
  AadeInvoiceGroup,
  Section,
  type AadeInvoiceTypeView,
  type DocumentSide,
} from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { unwrapList } from '@/components/data-table/list-response'

import { aadeLabel } from './values'

export interface AadeInvoiceTypeOption {
  value: string
  label: string
}

/**
 * The AADE codification, narrowed to one side of annex 8.1.
 *
 * ⚠️ **34 codes for a sales document type and 15 for a purchase one — never all 55.** The narrowing
 * is not a convenience: `SalesDocumentTypeServiceImpl.requireIssuerSide` refuses a sales type that
 * names a received code with a 422, so offering all 55 would build a picker most of whose options
 * are certain refusals. The six `ENTITY_ADJUSTING` codes are in **neither** side — they are the
 * entity's own journal entries, with no counterparty — which is a fact about the codification rather
 * than an omission here.
 *
 * Reference data governed by `TAX_AND_CHARGES`, which is **not** the section governing either
 * document-type screen — so this follows `lookups.ts`'s standing policy and asks for nothing when
 * the role holds no grant, rather than collecting a 403 the operator did not cause.
 */
export function useAadeInvoiceTypes(side: DocumentSide) {
  const permissions = usePermissions()
  const permitted = permissions.canView(Section.TAX_AND_CHARGES)

  const query = useAadeInvoiceTypeControllerInvoiceTypes(
    { side, active: true },
    { query: { enabled: permitted, staleTime: 5 * 60_000 } },
  )

  return { items: unwrapList(query.data).rows, permitted, isLoading: query.isLoading }
}

/**
 * Options grouped by annex 8.1 group, each group's rows in the codification's own order.
 *
 * ⚠️ **Every option reads `code — description`, and that is MANDATORY rather than cosmetic.**
 * Annex 8.1's description cell is empty for codes **4** and **12**, so V31 seeded both with the
 * group heading `Για Μελλοντική Χρήση` — which means two different statutory codes carry
 * *character-for-character identical* descriptions. A picker showing the description alone offers
 * two options a human cannot tell apart, and choosing the wrong one writes the wrong code into a
 * field transmitted to the tax authority. Leading with the code also rescues the three-character
 * descriptions in `ISSUER_UNMATCHED`.
 *
 * ⚠️ **Rows are NOT re-sorted within a group.** The backend returns them in the XSD's own
 * enumeration order, and `AadeInvoiceTypeController` says why sorting by code would be wrong: the
 * codes are dotted strings, so a text sort puts `10.1` before `2.1` and `13.31` before `13.4`.
 * Grouping reorders whole blocks; inside a block the authority's order stands.
 *
 * `OptionSelect` has no `optgroup` and inventing one would fork the one component this application
 * builds selects with, so the group's translated name is folded into each label instead.
 */
export function aadeOptions(
  types: readonly AadeInvoiceTypeView[],
  t: TFunction,
): AadeInvoiceTypeOption[] {
  return Object.values(AadeInvoiceGroup).flatMap((group) =>
    types
      .filter((type) => type.group === group)
      .map((type) => ({
        value: String(type.id),
        label: `${aadeLabel(type)}  ·  ${t(`AadeInvoiceGroup.${group}`, { ns: 'enums' })}`,
      })),
  )
}
