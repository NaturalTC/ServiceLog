import { CaretRight } from '@phosphor-icons/react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

// One line in a ruled list: a title, a line under it, and something at the end
// (a pill, a time, a count). The whole row is the tap target.
export function Row({
  to,
  title,
  sub,
  end,
}: {
  to?: string
  title: ReactNode
  sub?: ReactNode
  end?: ReactNode
}) {
  const content = (
    <>
      <span className="row-main">
        <span className="row-title">{title}</span>
        {sub && <span className="row-sub">{sub}</span>}
      </span>
      <span className="row-end">
        {end}
        {to && <CaretRight size={18} aria-hidden="true" />}
      </span>
    </>
  )
  return to ? (
    <Link to={to} className="row">
      {content}
    </Link>
  ) : (
    <div className="row-static">{content}</div>
  )
}
