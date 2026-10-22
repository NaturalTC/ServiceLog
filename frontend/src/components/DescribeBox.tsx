import { Sparkles } from 'lucide-react'
import { useState } from 'react'
import { ApiError, errorMessage, OfflineError } from '../api/client'

// "Describe it" (S35): type or dictate one sentence and AI fills in the form below.
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

  async function run() {
    if (!text.trim()) return
    setBusy(true)
    setMessage(null)
    try {
      await fill(text.trim())
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
        <Sparkles size={16} aria-hidden="true" />
        Describe it
      </h2>
      <p className="muted no-margin">Type or dictate it, and AI fills in the form below for you to check.</p>
      <label htmlFor="describe" className="visually-hidden">
        Describe it
      </label>
      <textarea id="describe" rows={2} placeholder={placeholder} value={text} onChange={(e) => setText(e.target.value)} />
      <button type="button" className="dark" onClick={run} disabled={busy || !text.trim()}>
        <Sparkles size={18} aria-hidden="true" />
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
