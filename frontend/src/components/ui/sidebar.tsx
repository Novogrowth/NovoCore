import * as React from 'react'
import { cn } from '@/lib/utils'

export function Sidebar({ className, children }: React.HTMLAttributes<HTMLElement>) {
  return <aside className={cn('flex h-screen w-72 flex-col border-r bg-background p-4', className)}>{children}</aside>
}

export function SidebarHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('mb-6', className)} {...props} />
}

export function SidebarContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex-1 space-y-2', className)} {...props} />
}

export function SidebarFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('mt-6 border-t pt-4', className)} {...props} />
}
