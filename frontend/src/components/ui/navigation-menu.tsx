import * as React from 'react'
import { cn } from '@/lib/utils'

export function NavigationMenu({ className, ...props }: React.HTMLAttributes<HTMLElement>) {
  return <nav className={cn('flex items-center gap-2', className)} {...props} />
}

export function NavigationMenuList({ className, ...props }: React.HTMLAttributes<HTMLUListElement>) {
  return <ul className={cn('flex items-center gap-2', className)} {...props} />
}

export function NavigationMenuItem({ className, ...props }: React.HTMLAttributes<HTMLLIElement>) {
  return <li className={cn('', className)} {...props} />
}

export function NavigationMenuLink({ className, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) {
  return <a className={cn('rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground', className)} {...props} />
}
