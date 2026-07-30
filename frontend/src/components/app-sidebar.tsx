import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

import { usePermissions } from '@/auth/permissions'
import { visibleNav, type VisibleNode } from '@/nav/visibility'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
  SidebarSeparator,
} from '@/components/ui/sidebar'

/**
 * Renders whatever `visibleNav` returns, and decides nothing itself.
 *
 * There is no list of screens in this file. Which items exist, in what order and under which
 * headings is `nav/tree.ts`; who may see them is `nav/visibility.ts`; and the router asks the same
 * function, so the menu and the set of reachable routes cannot drift apart.
 */

function Placeholder({ label }: { label: string }) {
  const { t } = useTranslation('common')
  return (
    <SidebarMenuButton
      disabled
      aria-disabled
      title={t('nav.notBuiltHint')}
      className="cursor-not-allowed opacity-50"
    >
      <span>{label}</span>
      <span className="ml-auto text-[10px] tracking-wide uppercase">{t('nav.notBuilt')}</span>
    </SidebarMenuButton>
  )
}

function Leaf({ entry }: { entry: VisibleNode }) {
  const { t } = useTranslation('nav')
  const label = t(entry.node.id)

  return (
    <SidebarMenuItem>
      {entry.enabled && entry.node.path ? (
        <SidebarMenuButton
          render={
            <NavLink to={entry.node.path}>
              <span>{label}</span>
            </NavLink>
          }
        />
      ) : (
        <Placeholder label={label} />
      )}
    </SidebarMenuItem>
  )
}

function SubLeaf({ entry }: { entry: VisibleNode }) {
  const { t } = useTranslation('nav')
  const { t: tc } = useTranslation('common')
  const label = t(entry.node.id)

  return (
    <>
      {entry.enabled && entry.node.path ? (
        <SidebarMenuSubButton render={<NavLink to={entry.node.path}>{label}</NavLink>} />
      ) : (
        // Not a link and not a button: there is nothing behind it, so it is text. A disabled
        // anchor is still focusable and still announces as a link, which is the wrong promise.
        <span
          aria-disabled
          title={tc('nav.notBuiltHint')}
          className="text-sidebar-foreground/50 flex h-7 cursor-not-allowed items-center gap-2 px-2 text-sm"
        >
          {label}
        </span>
      )}
      {entry.node.dividerAfter && <SidebarSeparator className="my-1" />}
    </>
  )
}

/** A nested heading — Inventory, Accounting, Settings — and the items beneath it. */
function Branch({ entry }: { entry: VisibleNode }) {
  const { t } = useTranslation('nav')

  if (entry.children.length === 0) return <Leaf entry={entry} />

  return (
    <SidebarMenuItem>
      <SidebarMenuButton disabled className="text-muted-foreground font-medium">
        <span>{t(entry.node.id)}</span>
      </SidebarMenuButton>
      <SidebarMenuSub>
        {entry.children.map((child) => (
          <SidebarMenuSubItem key={child.node.id}>
            {child.children.length > 0 ? <Branch entry={child} /> : <SubLeaf entry={child} />}
          </SidebarMenuSubItem>
        ))}
      </SidebarMenuSub>
    </SidebarMenuItem>
  )
}

/** A top-level heading: Operations, Finance, System. */
function TopGroup({ entry }: { entry: VisibleNode }) {
  const { t } = useTranslation('nav')

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{t(entry.node.id)}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {entry.children.map((child) => (
            <Branch key={child.node.id} entry={child} />
          ))}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  )
}

export function AppSidebar() {
  const permissions = usePermissions()
  const { t } = useTranslation('common')

  return (
    <Sidebar>
      <SidebarHeader className="px-4 py-3 text-lg font-semibold">{t('app.name')}</SidebarHeader>
      <SidebarContent>
        {visibleNav(permissions).map((entry) =>
          entry.children.length > 0 ? (
            <TopGroup key={entry.node.id} entry={entry} />
          ) : (
            <SidebarGroup key={entry.node.id}>
              <SidebarMenu>
                <Leaf entry={entry} />
              </SidebarMenu>
            </SidebarGroup>
          ),
        )}
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}
