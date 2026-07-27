import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { NavigationMenu, NavigationMenuItem, NavigationMenuList } from '@/components/ui/navigation-menu'
import { Sidebar, SidebarContent, SidebarFooter, SidebarHeader } from '@/components/ui/sidebar'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

function PlaceholderPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-muted-foreground">Foundation</p>
          <h1 className="text-3xl font-semibold tracking-tight">NovoCore frontend shell</h1>
        </div>
        <Badge>Structure only</Badge>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Layout placeholder</CardTitle>
          <CardDescription>This shell will later host the real NovoCore screens once the design system is connected.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-3">
            <Button>Primary action</Button>
            <Button variant="outline">Secondary action</Button>
          </div>
          <Input placeholder="Search placeholder" />
        </CardContent>
      </Card>

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="data">Data</TabsTrigger>
        </TabsList>
        <TabsContent value="overview" className="rounded-lg border p-4">
          <p className="text-sm text-muted-foreground">The app shell is ready for future screens and navigation.</p>
        </TabsContent>
        <TabsContent value="data" className="rounded-lg border p-4">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Area</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow>
                <TableCell>Routing</TableCell>
                <TableCell>Ready</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Design system components</TableCell>
                <TableCell>Ready</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </TabsContent>
      </Tabs>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <div className="flex min-h-screen bg-background">
        <Sidebar>
          <SidebarHeader>
            <div>
              <p className="text-sm font-medium text-muted-foreground">NovoCore</p>
              <h2 className="text-xl font-semibold">Frontend foundation</h2>
            </div>
          </SidebarHeader>
          <SidebarContent>
            <NavigationMenu className="flex-col items-start">
              <NavigationMenuList className="flex-col items-start">
                <NavigationMenuItem>
                  <NavLink to="/" end className={({ isActive }) => `rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground ${isActive ? 'bg-accent text-accent-foreground' : ''}`}>
                    Overview
                  </NavLink>
                </NavigationMenuItem>
                <NavigationMenuItem>
                  <NavLink to="/workspace" className={({ isActive }) => `rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground ${isActive ? 'bg-accent text-accent-foreground' : ''}`}>
                    Workspace
                  </NavLink>
                </NavigationMenuItem>
              </NavigationMenuList>
            </NavigationMenu>
          </SidebarContent>
          <SidebarFooter>
            <p className="text-sm text-muted-foreground">Foundation only — no real screens yet.</p>
          </SidebarFooter>
        </Sidebar>

        <main className="flex-1 p-6">
          <Routes>
            <Route path="/" element={<PlaceholderPage />} />
            <Route path="/workspace" element={<PlaceholderPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}

export default App
