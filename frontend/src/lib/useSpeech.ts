import { useEffect, useRef, useState } from 'react'

// Speech to text with the phone's own recognition (Safari / iOS, Chrome): free, and the
// audio never goes to the AI provider, so names stay private as everywhere else in the app.
// Where the browser has none, `supported` is false and the mic button stays hidden;
// keyboard dictation still works in every text box.

interface RecognitionResult {
  isFinal: boolean
  0: { transcript: string }
}
interface RecognitionEvent {
  resultIndex: number
  results: ArrayLike<RecognitionResult>
}
interface Recognition {
  lang: string
  interimResults: boolean
  continuous: boolean
  start(): void
  stop(): void
  abort(): void
  onresult: ((e: RecognitionEvent) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
}
type RecognitionConstructor = new () => Recognition

function recognitionClass(): RecognitionConstructor | undefined {
  const w = window as unknown as { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition
}

export const speechSupported = typeof window !== 'undefined' && Boolean(recognitionClass())

const ERRORS: Record<string, string> = {
  'not-allowed': 'Microphone access is off. Allow it in Settings (Safari > Microphone), then try again.',
  'service-not-allowed': 'Speech recognition is off. Turn on Siri & Dictation in Settings, then try again.',
  'audio-capture': 'No microphone found.',
  network: 'Speech recognition needs signal.',
}

export function useSpeech({
  onText,
  onFinal,
}: {
  onText?: (text: string) => void // while speaking: the words so far
  onFinal: (text: string) => void // when you stop: the full sentence
}) {
  const [listening, setListening] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const recognition = useRef<Recognition | null>(null)
  const callbacks = useRef({ onText, onFinal })
  useEffect(() => {
    callbacks.current = { onText, onFinal }
  })

  useEffect(() => () => recognition.current?.abort(), [])

  function start() {
    const Class = recognitionClass()
    if (!Class || listening) return
    setError(null)
    const r = new Class()
    r.lang = 'en-US'
    r.interimResults = true
    r.continuous = false // one command or sentence, ends on a pause
    let finalText = ''
    r.onresult = (e) => {
      let interim = ''
      finalText = ''
      for (let i = 0; i < e.results.length; i++) {
        const piece = e.results[i][0].transcript
        if (e.results[i].isFinal) finalText += piece
        else interim += piece
      }
      callbacks.current.onText?.((finalText + interim).trim())
    }
    r.onerror = (e) => {
      // Silence or a cancelled start isn't worth a message
      if (e.error !== 'no-speech' && e.error !== 'aborted') setError(ERRORS[e.error] ?? 'Couldn’t hear that. Try again.')
    }
    r.onend = () => {
      setListening(false)
      recognition.current = null
      if (finalText.trim()) callbacks.current.onFinal(finalText.trim())
    }
    recognition.current = r
    setListening(true)
    try {
      r.start()
    } catch {
      setListening(false)
      recognition.current = null
    }
  }

  function stop() {
    recognition.current?.stop() // what was heard so far still comes through onend
  }

  return { supported: speechSupported, listening, error, start, stop, toggle: () => (listening ? stop() : start()) }
}
