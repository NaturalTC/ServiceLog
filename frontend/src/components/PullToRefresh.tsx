import { ArrowClockwise } from '@phosphor-icons/react'
import { PULL_TO_REFRESH } from '../lib/pullToRefresh'

// The spinner shown while pulling down to refresh (see lib/pullToRefresh)
export function PullIndicator({ pull }: { pull: number }) {
  if (pull <= 0) return null
  const ready = pull >= PULL_TO_REFRESH
  return (
    <div className="pull-indicator" style={{ height: pull }} aria-hidden="true">
      <span
        className={`pull-spinner${ready ? ' ready' : ''}`}
        style={{ transform: `rotate(${(pull / PULL_TO_REFRESH) * 270}deg)`, opacity: Math.min(1, pull / 40) }}
      >
        <ArrowClockwise size={20} weight="bold" />
      </span>
    </div>
  )
}
