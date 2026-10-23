import type { ReactNode } from 'react'

// Small uppercase heading over a group, with an optional count on the right
export function SectionHeading({ id, children, count }: { id?: string; children: ReactNode; count?: number }) {
  return (
    <div className="section-heading">
      <h2 id={id}>{children}</h2>
      {count !== undefined && <span className="count">{count}</span>}
    </div>
  )
}
