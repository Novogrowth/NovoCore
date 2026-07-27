import { NavLink } from 'react-router-dom'
import { Boxes, LayoutDashboard, Settings } from 'lucide-react'

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
  SidebarRail,
} from '@/components/ui/sidebar'

// Structural placeholder navigation only — not real NovoCore pages.
const items = [
  { title: 'Overview', to: '/', icon: LayoutDashboard },
  { title: 'Section Two', to: '/section-two', icon: Boxes },
  { title: 'Section Three', to: '/section-three', icon: Settings },
]

export function AppSidebar() {
  return (
    <Sidebar>
      <SidebarHeader className="px-4 py-3 text-lg font-semibold">
        NovoCore
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Navigation</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {items.map((item) => (
                <SidebarMenuItem key={item.title}>
                  <SidebarMenuButton
                    render={
                      <NavLink to={item.to} end={item.to === '/'}>
                        <item.icon />
                        <span>{item.title}</span>
                      </NavLink>
                    }
                  />
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}
