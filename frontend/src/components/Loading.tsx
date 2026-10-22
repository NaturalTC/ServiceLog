// Placeholder cards while a page loads, shaped like the content that's coming,
// so the screen doesn't jump when it arrives
export function Loading({ cards = 3 }: { cards?: number }) {
  return (
    <div className="skeleton-list" role="status">
      <span className="visually-hidden">Loading…</span>
      {Array.from({ length: cards }, (_, i) => (
        <div key={i} className="skeleton-card" aria-hidden="true">
          <span className="skeleton-line wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line short" />
        </div>
      ))}
    </div>
  )
}
