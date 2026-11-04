import { useId, type ReactNode } from 'react'

// A labelled group on a page ("Customer", "Where", "Notes"), so what belongs together
// sits together and the page reads as a few clear blocks instead of one long stack
export function Panel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  const id = useId()
  return (
    <section className="panel" aria-labelledby={id}>
      <div className="panel-head">
        <h2 id={id} className="panel-title">
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  )
}
