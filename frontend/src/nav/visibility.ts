import type { Permissions } from '@/auth/permissions'

import { NAV_TREE } from './tree'
import type { NavNode } from './types'

/**
 * Which navigation items a given user sees, and which of them are reachable.
 *
 * One function, used by the sidebar **and** by the route guard. That is the point: if the menu and
 * the router each decided this for themselves, a hidden page would still render for anyone who
 * typed its URL, and the two would drift apart the first time either was edited.
 */

export interface VisibleNode {
  readonly node: NavNode
  /** False when the item is a placeholder: shown, but disabled and not routable. */
  readonly enabled: boolean
  readonly children: readonly VisibleNode[]
}

/** Does the user hold every grant this item names? */
function isPermitted(node: NavNode, permissions: Permissions): boolean {
  return node.requires.every((requirement) =>
    requirement.level === 'FULL'
      ? permissions.canEdit(requirement.section)
      : permissions.canView(requirement.section),
  )
}

/**
 * Is there actually something behind this item?
 *
 * The tree's `status` is what this frontend believes. `/api/me` reports, per section, whether
 * anything is built behind it — `Section.isAvailable`, which exists on the backend for exactly
 * this purpose. **The backend's answer can only downgrade the tree's claim, never contradict it
 * silently**: an item marked BUILT whose section the backend says is unavailable renders as a
 * placeholder. So the two cannot disagree in the dangerous direction, which is the frontend
 * offering a working link to something that does not exist.
 */
function isBuilt(node: NavNode, permissions: Permissions): boolean {
  if (node.status === 'NOT_BUILT') return false
  return node.requires.every((requirement) => permissions.isAvailable(requirement.section))
}

function visit(node: NavNode, permissions: Permissions): VisibleNode | undefined {
  const children = (node.children ?? [])
    .map((child) => visit(child, permissions))
    .filter((child): child is VisibleNode => child !== undefined)

  // A heading exists to hold its children. It appears when ANY descendant appears — computed
  // bottom-up, never from the first child: a role granted Settlements but not Sales must still
  // see the Finance heading above its one visible item.
  if (!node.path) {
    return children.length > 0 ? { node, enabled: false, children } : undefined
  }

  if (!isPermitted(node, permissions)) return undefined

  const built = isBuilt(node, permissions)

  // A placeholder is for whoever is deciding what to build next, not for whoever is trying to
  // work. Full access — OWNER and ADMIN — sees the shape of the system; everyone else sees only
  // doors that open.
  if (!built && !permissions.isFullAccess) {
    return children.length > 0 ? { node, enabled: false, children } : undefined
  }

  return { node, enabled: built, children }
}

export function visibleNav(
  permissions: Permissions,
  tree: readonly NavNode[] = NAV_TREE,
): readonly VisibleNode[] {
  return tree
    .map((node) => visit(node, permissions))
    .filter((node): node is VisibleNode => node !== undefined)
}

/** Flattens a visible tree to its reachable routes — what the router is allowed to render. */
export function reachablePaths(visible: readonly VisibleNode[]): Set<string> {
  const paths = new Set<string>()
  const walk = (nodes: readonly VisibleNode[]): void => {
    for (const entry of nodes) {
      if (entry.enabled && entry.node.path) paths.add(entry.node.path)
      walk(entry.children)
    }
  }
  walk(visible)
  return paths
}

/** Every node in the tree, depth-first. Used by the router and by the tree's own tests. */
export function flatten(tree: readonly NavNode[] = NAV_TREE): NavNode[] {
  const nodes: NavNode[] = []
  const walk = (entries: readonly NavNode[]): void => {
    for (const node of entries) {
      nodes.push(node)
      if (node.children) walk(node.children)
    }
  }
  walk(tree)
  return nodes
}

/** The node owning a route, or undefined if the path is not in the tree at all. */
export function nodeFor(path: string, tree: readonly NavNode[] = NAV_TREE): NavNode | undefined {
  return flatten(tree).find((node) => node.path === path)
}
