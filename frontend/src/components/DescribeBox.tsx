import { Lightning } from '@phosphor-icons/react'
import { useRef, useState } from 'react'
import { ApiError, errorMessage, OfflineError } from '../api/client'
import { useSpeech } from '../lib/useSpeech'
import { MicButton } from './MicButton'

// "Describe it" (S35): type or say one sentence and AI fills in the form below.
// Said with the mic, it fills in as soon as you stop talking.
// Nothing is saved until the owner checks it and taps Save.
export function DescribeBox({
  placeholder,
  fill,
}: {
  placeholder: string
  // Calls the AI and fills the form; resolves when done
  fill: (text: string) => Promise<void>
}) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null)

  // Spoken words add to whatever was already typed
  const before = useRef('')
  const speech = useSpeech({
    onText: (words) => setText(`${before.current}${words}`),
    onFinal: (words) => {
      const all = `${before.current}${words}`
      setText(all)
      void run(all)
    },
  })

  function toggleMic() {
    if (!speech.listening) before.current = text.trim() ? `${text.trim()} ` : ''
    speech.toggle()
  }

  async function run(value = text) {
    if (!value.trim()) return
    setBusy(true)
    setMessage(null)
    try {
      await fill(value.trim())
      setMessage({ text: 'Filled in by AI. Check every field before saving.', ok: true })
    } catch (err) {
      const offline = err instanceof OfflineError
      setMessage({
        text: offline
          ? 'This needs signal. Fill in the form by hand.'
          : err instanceof ApiError
            ? err.message
            : errorMessage(err),
        ok: false,
      })
    }
    setBusy(false)
  }

  return (
    <section className="ai-box" aria-labelledby="describe-title">
      <h2 id="describe-title">
        <Lightning size={16} aria-hidden="true" />
        Describe it
      </h2>
      <p className="muted no-margin">
        {speech.supported ? 'Type it or tap the mic and say it' : 'Type or dictate it'}, and AI fills in the form below for
        you to check.
      </p>
      <label htmlFor="describe" className="visually-hidden">
        Describe it
      </label>
      <div className="describe-input">
        <textarea
          id="describe"
          rows={2}
          placeholder={speech.listening ? 'Listening…' : placeholder}
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        {speech.supported && <MicButton listening={speech.listening} onClick={toggleMic} label="Say it" />}
      </div>
      {speech.error && (
        <p className="error no-margin" role="alert">
          {speech.error}
        </p>
      )}
      <button type="button" className="dark" onClick={() => run()} disabled={busy || !text.trim() || speech.listening}>
        <Lightning size={18} aria-hidden="true" />
        {busy ? 'Filling in…' : 'Fill in with AI'}
      </button>
      {message && (
        <p className={message.ok ? 'notice no-margin' : 'error no-margin'} role="status">
          {message.text}
        </p>
      )}
    </section>
  )
}
