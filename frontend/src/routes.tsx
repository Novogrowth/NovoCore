import type { ReactElement } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'

import { usePermissions } from '@/auth/permissions'
import { NAV_TREE } from '@/nav/tree'
import type { NavNode } from '@/nav/types'
import { flatten, reachablePaths, visibleNav } from '@/nav/visibility'
import { NoAccess, NotFound, ScreenPlaceholder } from '@/pages/placeholder'
import { ProductCreate } from '@/pages/products/product-create'
import { ProductDetail } from '@/pages/products/product-detail'
import { ProductsList } from '@/pages/products/products-list'
import { CreditNoteDetail } from '@/pages/credit-notes/credit-note-detail'
import { CreditNoteRecord } from '@/pages/credit-notes/credit-note-record'
import { CreditNotesList } from '@/pages/credit-notes/credit-notes-list'
import { CustomerCreate } from '@/pages/customers/customer-create'
import { CustomerDetail } from '@/pages/customers/customer-detail'
import { CustomersList } from '@/pages/customers/customers-list'
import { AdaptersGrid, ModulesGrid } from '@/pages/registry-grid'
import { SalesInvoiceDetail } from '@/pages/sales/sales-invoice-detail'
import { SalesInvoiceRecord } from '@/pages/sales/sales-invoice-record'
import { SalesInvoicesList } from '@/pages/sales/sales-invoices-list'
import {
  DocumentSettings,
  EmailSettings,
  RetentionSettings,
} from '@/pages/settings/settings-page'
import {
  AadeInvoiceTypeDetail,
  AadeInvoiceTypesList,
} from '@/pages/aade-invoice-types/aade-invoice-types'
import {
  DeliveryMethodCreate,
  DeliveryMethodDetail,
  DeliveryMethodsList,
} from '@/pages/delivery-methods/delivery-methods'
import {
  PaymentMethodCreate,
  PaymentMethodDetail,
  PaymentMethodsList,
} from '@/pages/payment-methods/payment-methods'
import {
  PurchaseDocumentSeriesCreate,
  PurchaseDocumentSeriesDetail,
  PurchaseDocumentSeriesList,
} from '@/pages/purchase-document-series/purchase-document-series'
import {
  PurchaseDocumentTypeCreate,
  PurchaseDocumentTypeDetail,
  PurchaseDocumentTypesList,
} from '@/pages/purchase-document-types/purchase-document-types'
import {
  SalesDocumentSeriesCreate,
  SalesDocumentSeriesDetail,
  SalesDocumentSeriesList,
} from '@/pages/sales-document-series/sales-document-series'
import {
  SalesDocumentTypeCreate,
  SalesDocumentTypeDetail,
  SalesDocumentTypesList,
} from '@/pages/sales-document-types/sales-document-types'
import { UnitCreate } from '@/pages/units-of-measure/unit-create'
import { UnitDetail } from '@/pages/units-of-measure/unit-detail'
import { UnitsList } from '@/pages/units-of-measure/units-list'
import { VatClassCreate } from '@/pages/vat-classes/vat-class-create'
import { VatClassDetail } from '@/pages/vat-classes/vat-class-detail'
import { VatClassesList } from '@/pages/vat-classes/vat-classes-list'
import { RoleCreate } from '@/pages/roles/role-create'
import { RoleDetail } from '@/pages/roles/role-detail'
import { RolesList } from '@/pages/roles/roles-list'
import { SupplierCreate } from '@/pages/suppliers/supplier-create'
import { SupplierDetail } from '@/pages/suppliers/supplier-detail'
import { SuppliersList } from '@/pages/suppliers/suppliers-list'
import { UserCreate } from '@/pages/users/user-create'
import { UserDetail } from '@/pages/users/user-detail'
import { UsersList } from '@/pages/users/users-list'

/**
 * The routes, derived from the navigation tree rather than listed again.
 *
 * Every routable node gets a route, and what it renders is decided by the same `visibleNav` the
 * sidebar uses. That is the point of doing it this way: typing the URL of a page that is hidden
 * from your role, or of one that is not built, cannot produce a blank screen or a 403 in the
 * console — it produces the same answer the menu would have given.
 */

/** Routes with a real screen behind them. Everything else in the tree is a placeholder. */
const SCREENS: Record<string, () => ReactElement> = {
  '/products': ProductsList,
  '/suppliers': SuppliersList,
  '/customers': CustomersList,
  '/users': UsersList,
  '/roles': RolesList,
  '/settings/adapters': AdaptersGrid,
  '/settings/modules': ModulesGrid,
  '/settings/documents': DocumentSettings,
  '/settings/email': EmailSettings,
  '/settings/retention': RetentionSettings,
  '/settings/vat-classes': VatClassesList,
  '/settings/units-of-measure': UnitsList,
  '/settings/sales-document-types': SalesDocumentTypesList,
  '/settings/sales-document-series': SalesDocumentSeriesList,
  '/settings/purchase-document-types': PurchaseDocumentTypesList,
  '/settings/purchase-document-series': PurchaseDocumentSeriesList,
  '/settings/delivery-methods': DeliveryMethodsList,
  '/settings/payment-methods': PaymentMethodsList,
  '/settings/aade-invoice-types': AadeInvoiceTypesList,
  '/sales/invoices': SalesInvoicesList,
  '/sales/credit-notes': CreditNotesList,
}

/**
 * Routes that belong to a menu item without being one.
 *
 * A product's detail page is not a menu entry — nobody navigates to "product 41" from a sidebar —
 * but it is still governed by the grant that governs Products. `owner` names the nav node whose
 * permission decides, so these cannot drift out of step with the menu, and adding one does not mean
 * inventing a second permission rule.
 *
 * Order matters: `/products/new` is declared before `/products/:id`, or React Router matches the
 * literal path as an id.
 */
const CHILD_ROUTES: { path: string; owner: string; element: () => ReactElement }[] = [
  { path: '/products/new', owner: 'products', element: ProductCreate },
  { path: '/products/:id', owner: 'products', element: ProductDetail },
  { path: '/suppliers/new', owner: 'suppliers', element: SupplierCreate },
  { path: '/suppliers/:id', owner: 'suppliers', element: SupplierDetail },
  { path: '/customers/new', owner: 'customers', element: CustomerCreate },
  { path: '/customers/:id', owner: 'customers', element: CustomerDetail },
  { path: '/users/new', owner: 'users', element: UserCreate },
  { path: '/users/:id', owner: 'users', element: UserDetail },
  { path: '/roles/new', owner: 'roles', element: RoleCreate },
  { path: '/roles/:id', owner: 'roles', element: RoleDetail },
  // Reference data. `owner` is the nav node whose grant decides — `TAX_AND_CHARGES` for VAT
  // classes and `PRODUCTS` for units of measure, neither of which is `SETTINGS` despite both
  // living under the Settings heading.
  { path: '/settings/vat-classes/new', owner: 'settings.vatClasses', element: VatClassCreate },
  { path: '/settings/vat-classes/:id', owner: 'settings.vatClasses', element: VatClassDetail },
  { path: '/settings/units-of-measure/new', owner: 'settings.unitsOfMeasure', element: UnitCreate },
  { path: '/settings/units-of-measure/:id', owner: 'settings.unitsOfMeasure', element: UnitDetail },
  // R2's document reference data.
  { path: '/settings/sales-document-types/new', owner: 'settings.salesDocumentTypes', element: SalesDocumentTypeCreate },
  { path: '/settings/sales-document-types/:id', owner: 'settings.salesDocumentTypes', element: SalesDocumentTypeDetail },
  { path: '/settings/sales-document-series/new', owner: 'settings.salesDocumentSeries', element: SalesDocumentSeriesCreate },
  { path: '/settings/sales-document-series/:id', owner: 'settings.salesDocumentSeries', element: SalesDocumentSeriesDetail },
  { path: '/settings/purchase-document-types/new', owner: 'settings.purchaseDocumentTypes', element: PurchaseDocumentTypeCreate },
  { path: '/settings/purchase-document-types/:id', owner: 'settings.purchaseDocumentTypes', element: PurchaseDocumentTypeDetail },
  { path: '/settings/purchase-document-series/new', owner: 'settings.purchaseDocumentSeries', element: PurchaseDocumentSeriesCreate },
  { path: '/settings/purchase-document-series/:id', owner: 'settings.purchaseDocumentSeries', element: PurchaseDocumentSeriesDetail },
  { path: '/settings/delivery-methods/new', owner: 'settings.deliveryMethods', element: DeliveryMethodCreate },
  { path: '/settings/delivery-methods/:id', owner: 'settings.deliveryMethods', element: DeliveryMethodDetail },
  // ⚠️ NO `/new` for the AADE codification, and its absence here is load-bearing: even if somebody
  // added a create form, there would be no route to reach it by. Row authorship is Flyway's.
  { path: '/settings/aade-invoice-types/:id', owner: 'settings.aadeInvoiceTypes', element: AadeInvoiceTypeDetail },
  // ⚠️ NO `/new`, for the same reason as the AADE codification: adding a payment method needs an
  // AccountSystemKey and two behaviour flags, so it is a code change rather than a form.
  { path: '/settings/payment-methods/new', owner: 'settings.paymentMethods', element: PaymentMethodCreate },
  { path: '/settings/payment-methods/:id', owner: 'settings.paymentMethods', element: PaymentMethodDetail },
  // ⚠️ NO `/sales/invoices/:id/edit`, and the absence is the design rather than a gap. A posted
  // document is immutable (ADR 0006) and the backend has no route to change one — measured, not
  // assumed: PATCH answers 404 and DELETE answers 405. Correction is reversal or a credit note.
  { path: '/sales/invoices/new', owner: 'sales.invoices', element: SalesInvoiceRecord },
  { path: '/sales/invoices/:id', owner: 'sales.invoices', element: SalesInvoiceDetail },
  // ⚠️ NO `/sales/credit-notes/:id/edit`, for the same two reasons: a posted document is immutable,
  // and this one exists outside Novocore. Correction is reversal — which is itself transitional,
  // because once the adapter exists the fix for a wrong mirror is a re-fetch (`CLAUDE.md` §1b).
  { path: '/sales/credit-notes/new', owner: 'sales.creditNotes', element: CreditNoteRecord },
  { path: '/sales/credit-notes/:id', owner: 'sales.creditNotes', element: CreditNoteDetail },
]

function RouteElement({ node, screen }: { node: NavNode; screen?: () => ReactElement }) {
  const permissions = usePermissions()
  const reachable = reachablePaths(visibleNav(permissions))

  // Still loading /api/me. Rendering "no access" here would flash a refusal at someone who has
  // access, which is worse than a moment of nothing.
  if (permissions.isLoading) return null

  if (!node.path || !reachable.has(node.path)) {
    // Either the role does not hold the grant, or nothing is built behind it. Both are "not
    // available to you" from where the person is standing; the menu says which by not being
    // clickable, and this page does not speculate.
    return <NoAccess />
  }

  const Screen = screen ?? SCREENS[node.path]
  if (Screen) return <Screen />

  return <ScreenPlaceholder titleKey={node.id} endpoint={node.endpoint} />
}

export function AppRoutes() {
  const nodes = flatten(NAV_TREE)
  const routable = nodes.filter((node) => node.path)

  return (
    <Routes>
      {/* Products is the busiest screen in the building and the obvious landing page. */}
      <Route index element={<Navigate to="/products" replace />} />

      {CHILD_ROUTES.map((child) => {
        const owner = nodes.find((node) => node.id === child.owner)
        return owner ? (
          <Route
            key={child.path}
            path={child.path}
            element={<RouteElement node={owner} screen={child.element} />}
          />
        ) : null
      })}

      {routable.map((node) => (
        <Route key={node.path} path={node.path} element={<RouteElement node={node} />} />
      ))}

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
