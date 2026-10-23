import type { Icon } from '@phosphor-icons/react'
import type { ReactNode } from 'react'

// Nothing to show yet: a short, friendly line and the one useful next step
export function EmptyState({ icon: Shape, children, action }: { icon: Icon; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <Shape size={40} weight="light" aria-hidden="true" />
      <p>{children}</p>
      {action}
    </div>
  )
}
