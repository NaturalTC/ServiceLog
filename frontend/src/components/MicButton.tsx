import { Microphone, Stop } from '@phosphor-icons/react'

// Tap to speak, tap again to stop (it also stops by itself after a pause)
export function MicButton({
  listening,
  onClick,
  label = 'Speak',
  className = '',
}: {
  listening: boolean
  onClick: () => void
  label?: string
  className?: string
}) {
  return (
    <button
      type="button"
      className={`mic${listening ? ' listening' : ''} ${className}`.trim()}
      onClick={onClick}
      aria-pressed={listening}
      aria-label={listening ? 'Stop listening' : label}
    >
      {listening ? <Stop size={18} weight="fill" aria-hidden="true" /> : <Microphone size={20} aria-hidden="true" />}
    </button>
  )
}
