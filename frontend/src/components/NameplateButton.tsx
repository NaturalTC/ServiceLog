import { Scan } from '@phosphor-icons/react'
import { useState, type ChangeEvent } from 'react'
import { assistantApi, type NameplateReading } from '../api/assistant'
import { ApiError, errorMessage, OfflineError } from '../api/client'
import { shrinkPhoto } from '../lib/photos'

// "Read nameplate" (S36): photograph the unit's data plate and AI fills in brand, model and
// serial number for the owner to check. The photo isn't kept.
export function NameplateButton({ onRead }: { onRead: (reading: NameplateReading) => void }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null)

  async function picked(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // so the same photo can be picked again
    if (!file) return
    setBusy(true)
    setMessage(null)
    try {
      const reading = await assistantApi.nameplate(await shrinkPhoto(file))
      if (!reading.readable || (!reading.model && !reading.serialNumber)) {
        setMessage({
          text: "Couldn't read the plate clearly. Try again closer and straight on, without glare, or type it in.",
          ok: false,
        })
      } else {
        onRead(reading)
        const check = reading.confidence === 'high' ? 'Check' : 'Read with some doubt: check'
        setMessage({ text: `${check} the model and serial number against the plate before saving.`, ok: true })
      }
    } catch (err) {
      setMessage({
        text:
          err instanceof OfflineError
            ? 'This needs signal. Type the details in instead.'
            : err instanceof ApiError
              ? err.message
              : errorMessage(err),
        ok: false,
      })
    }
    setBusy(false)
  }

  return (
    <div className="nameplate">
      <label className={`button secondary file-button${busy ? ' disabled' : ''}`}>
        <Scan size={18} aria-hidden="true" />
        {busy ? 'Reading the plate…' : 'Read nameplate'}
        <input type="file" accept="image/*" capture="environment" onChange={picked} disabled={busy} />
      </label>
      {message && (
        <p className={message.ok ? 'notice no-margin' : 'error no-margin'} role="status">
          {message.text}
        </p>
      )}
    </div>
  )
}
