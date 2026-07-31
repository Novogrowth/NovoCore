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
import { CustomerCreate } from '@/pages/customers/customer-create'
import { CustomerDetail } from '@/pages/customers/customer-detail'
import { CustomersList } from '@/pages/customers/customers-list'
import { AdaptersGrid, ModulesGrid } from '@/pages/registry-grid'
import { SupplierCreate } from '@/pages/suppliers/supplier-create'
import { SupplierDetail } from '@/pages/suppliers/supplier-detail'
import { SuppliersList } from '@/pages/suppliers/suppliers-list'

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
  '/settings/adapters': AdaptersGrid,
  '/settings/modules': ModulesGrid,
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
