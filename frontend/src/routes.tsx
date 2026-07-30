import { Navigate, Route, Routes } from 'react-router-dom'

import { usePermissions } from '@/auth/permissions'
import { NAV_TREE } from '@/nav/tree'
import type { NavNode } from '@/nav/types'
import { flatten, reachablePaths, visibleNav } from '@/nav/visibility'
import { AdaptersGrid, ModulesGrid } from '@/pages/registry-grid'
import { NoAccess, NotFound, ScreenPlaceholder } from '@/pages/placeholder'

/**
 * The routes, derived from the navigation tree rather than listed again.
 *
 * Every routable node gets a route, and what it renders is decided by the same `visibleNav` the
 * sidebar uses. That is the point of doing it this way: typing the URL of a page that is hidden
 * from your role, or of one that is not built, cannot produce a blank screen or a 403 in the
 * console — it produces the same answer the menu would have given.
 */

/** The handful of routes that have a real screen today. Everything else is a placeholder. */
const SCREENS: Record<string, () => React.ReactElement> = {
  '/settings/adapters': AdaptersGrid,
  '/settings/modules': ModulesGrid,
}

function RouteElement({ node }: { node: NavNode }) {
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

  const Screen = SCREENS[node.path]
  if (Screen) return <Screen />

  return <ScreenPlaceholder titleKey={node.id} endpoint={node.endpoint} />
}

export function AppRoutes() {
  const routable = flatten(NAV_TREE).filter((node) => node.path)

  return (
    <Routes>
      {/* Products is the busiest screen in the building and the obvious landing page. */}
      <Route index element={<Navigate to="/products" replace />} />
      {routable.map((node) => (
        <Route key={node.path} path={node.path} element={<RouteElement node={node} />} />
      ))}
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
