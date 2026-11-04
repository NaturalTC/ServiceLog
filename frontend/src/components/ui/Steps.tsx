// A short form split into steps ("Who → When → What"): where you are, and a tap goes back
// to any step you've already been through
export function Steps({
  steps,
  current,
  reached,
  onGo,
}: {
  steps: string[]
  current: number
  reached: number // furthest step opened so far
  onGo: (step: number) => void
}) {
  return (
    <ol className="steps" aria-label={`Step ${current + 1} of ${steps.length}`}>
      {steps.map((label, i) => (
        <li key={label} className={i < current ? 'done' : i === current ? 'current' : ''}>
          <button
            type="button"
            onClick={() => onGo(i)}
            disabled={i > reached}
            aria-current={i === current ? 'step' : undefined}
          >
            <span className="step-no">{i + 1}</span>
            {label}
          </button>
        </li>
      ))}
    </ol>
  )
}
