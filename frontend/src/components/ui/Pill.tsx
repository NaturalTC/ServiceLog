import type { ReactNode } from 'react'

export type PillTone = 'neutral' | 'now' | 'done' | 'urgent' | 'warning'

// A short status: "Now", "Done", "Urgent", "Ends in 21 days"
export function Pill({ tone = 'neutral', children }: { tone?: PillTone; children: ReactNode }) {
  return <span className={`pill ${tone}`}>{children}</span>
}
