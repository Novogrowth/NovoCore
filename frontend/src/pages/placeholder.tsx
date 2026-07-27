// Generic structural placeholder for the main content area.
// Intentionally contains no real NovoCore functionality.
export function Placeholder({ title }: { title: string }) {
  return (
    <div className="flex flex-1 items-center justify-center rounded-xl border border-dashed">
      <p className="text-muted-foreground">{title} — placeholder content area</p>
    </div>
  )
}
