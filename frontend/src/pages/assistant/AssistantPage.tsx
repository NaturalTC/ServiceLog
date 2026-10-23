import {
  ArrowUp,
  CalendarBlank,
  CalendarPlus,
  CaretRight,
  ClockCounterClockwise,
  Lightbulb,
  ListChecks,
  LockSimple,
  NavigationArrow,
  NotePencil,
  Phone,
  ShieldCheck,
  User,
  Wrench,
  type Icon,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { assistantApi, type CustomerCard, type Proposal } from '../../api/assistant'
import { ApiError, errorMessage, OfflineError } from '../../api/client'
import { ProposalCard } from '../../components/ProposalCard'
import { RichText } from '../../components/RichText'
import { directionsUrl, formatPhone } from '../../lib/format'

// "Ask ServiceLog" (S33): questions in plain words, answered from the app's own data.
// The chat lives in this browser tab (sessionStorage), so going to a customer and back
// keeps it; it's cleared on log out and by "New chat".

interface Message {
  role: 'user' | 'assistant' | 'error'
  text: string
  customers?: CustomerCard[]
  proposals?: Proposal[]
  suggestions?: boolean
}

interface Chat {
  conversationId: string | null
  messages: Message[]
}

export const CHAT_KEY = 'servicelog.chat'

// Each with an icon and a tint, like a grouped list in iOS Settings
const SUGGESTIONS: { text: string; icon: Icon; tint: string }[] = [
  { text: "What's on tomorrow?", icon: CalendarBlank, tint: 'blue' },
  { text: 'Book a tune-up for … next week', icon: CalendarPlus, tint: 'green' },
  { text: 'Who’s due for a tune-up?', icon: Wrench, tint: 'orange' },
  { text: 'Any follow-ups I still owe?', icon: ListChecks, tint: 'purple' },
  { text: 'Which warranties end soon?', icon: ShieldCheck, tint: 'teal' },
  { text: 'What did I do at the … house last time?', icon: ClockCounterClockwise, tint: 'pink' },
]

function greeting(): string {
  const hour = new Date().getHours()
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}

function loadChat(): Chat {
  try {
    const saved = sessionStorage.getItem(CHAT_KEY)
    if (saved) return JSON.parse(saved) as Chat
  } catch {
    // ignore: start a new chat
  }
  return { conversationId: null, messages: [] }
}

export function AssistantPage() {
  const [chat, setChat] = useState<Chat>(loadChat)
  // "Troubleshoot" on a unit's page opens the chat with the question started (S23)
  const started = (useLocation().state as { ask?: string } | null)?.ask
  const [input, setInput] = useState(started ?? '')
  const [thinking, setThinking] = useState(false)
  const end = useRef<HTMLDivElement>(null)
  const box = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    try {
      sessionStorage.setItem(CHAT_KEY, JSON.stringify(chat))
    } catch {
      // storage full or blocked: the chat still works for this visit
    }
    // An empty chat starts at the top, with the greeting
    if (chat.messages.length > 0 || thinking) end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [chat, thinking])

  async function send(text: string) {
    const message = text.trim()
    if (!message || thinking) return
    setInput('')
    const asked: Chat = { ...chat, messages: [...chat.messages, { role: 'user', text: message }] }
    setChat(asked)
    setThinking(true)
    try {
      const reply = await assistantApi.ask(chat.conversationId, message)
      setChat({
        conversationId: reply.conversationId,
        messages: [
          ...asked.messages,
          {
            role: 'assistant',
            text: reply.reply,
            customers: reply.customers,
            proposals: reply.proposals,
            suggestions: reply.suggestions,
          },
        ],
      })
    } catch (err) {
      const text =
        err instanceof OfflineError
          ? 'Asking needs signal. Your saved pages still work without it.'
          : err instanceof ApiError && err.code === 'AI_UNAVAILABLE'
            ? err.message
            : errorMessage(err)
      setChat({ ...asked, messages: [...asked.messages, { role: 'error', text }] })
    }
    setThinking(false)
  }

  // A confirmed card stays confirmed when coming back to the chat
  function markDone(message: number, proposal: number, outcome: Proposal['outcome']) {
    setChat((c) => ({
      ...c,
      messages: c.messages.map((m, i) =>
        i !== message ? m : { ...m, proposals: m.proposals?.map((p, j) => (j === proposal ? { ...p, outcome } : p)) },
      ),
    }))
  }

  function newChat() {
    if (chat.conversationId) assistantApi.end(chat.conversationId).catch(() => {})
    setChat({ conversationId: null, messages: [] })
    box.current?.focus()
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void send(input)
  }

  // Enter sends; Shift+Enter starts a new line
  function onKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void send(input)
    }
  }

  function suggest(text: string) {
    if (text.includes('…')) {
      setInput(text.replace('…', ''))
      box.current?.focus()
    } else {
      void send(text)
    }
  }

  return (
    <main className="assistant">
      <header className="ask-header">
        <h1>Ask</h1>
        {chat.messages.length > 0 && (
          <button type="button" className="compose" onClick={newChat} aria-label="New chat">
            <NotePencil size={20} aria-hidden="true" />
          </button>
        )}
      </header>

      {chat.messages.length === 0 && (
        <div className="ask-welcome">
          <p className="ask-hello">
            {greeting()}.
            <span>What do you need to know?</span>
          </p>
          <p className="ask-intro">Answers come from your own customers, equipment and schedule.</p>
          <h2 className="ask-group-label">Try asking</h2>
          <ul className="ask-suggestions">
            {SUGGESTIONS.map((s) => (
              <li key={s.text}>
                <button type="button" onClick={() => suggest(s.text)}>
                  <span className={`ask-tile ${s.tint}`} aria-hidden="true">
                    <s.icon size={18} weight="fill" />
                  </span>
                  <span className="ask-suggestion-text">{s.text}</span>
                  <CaretRight size={16} className="ask-chevron" aria-hidden="true" />
                </button>
              </li>
            ))}
          </ul>
          <p className="ask-footnote">
            <LockSimple size={14} weight="fill" aria-hidden="true" />
            Names, phone numbers and addresses are replaced with placeholders before anything is sent to the AI.
          </p>
        </div>
      )}

      <ol className="messages" aria-live="polite">
        {chat.messages.map((m, i) => (
          <li key={i} className={`message ${m.role}`}>
            <div className="message-body">
              {m.suggestions && (
                <span className="suggestions-label">
                  <Lightbulb size={14} aria-hidden="true" />
                  Suggestions to check, not a diagnosis
                </span>
              )}
              <div className="bubble">
                {m.role === 'user' ? <p>{m.text}</p> : <RichText text={m.text} />}
              </div>
              {m.proposals?.map((p, j) => (
                <ProposalCard key={j} proposal={p} onDone={(outcome) => markDone(i, j, outcome)} />
              ))}
              {m.customers && m.customers.length > 0 && <CustomerResults customers={m.customers} />}
            </div>
          </li>
        ))}
        {thinking && (
          <li className="message assistant">
            <div className="bubble typing" role="status">
              <span className="visually-hidden">Looking that up…</span>
              <i />
              <i />
              <i />
            </div>
          </li>
        )}
      </ol>
      <div ref={end} className="chat-end" />

      <form className="ask-bar" onSubmit={submit}>
        <label htmlFor="ask" className="visually-hidden">
          Ask ServiceLog
        </label>
        <textarea
          id="ask"
          ref={box}
          autoFocus={Boolean(started)}
          rows={1}
          placeholder="Ask anything"
          value={input}
          maxLength={2000}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKey}
        />
        <button type="submit" className="send" disabled={!input.trim() || thinking} aria-label="Send">
          <ArrowUp size={20} aria-hidden="true" />
        </button>
      </form>
    </main>
  )
}

// A long list answer would bury the chat in cards: three, then "Show more"
function CustomerResults({ customers }: { customers: CustomerCard[] }) {
  const [all, setAll] = useState(customers.length <= 3)
  const shown = all ? customers : customers.slice(0, 3)
  return (
    <>
      {shown.map((c) => (
        <CustomerResult key={c.id} customer={c} />
      ))}
      {!all && (
        <button type="button" className="link-button" onClick={() => setAll(true)}>
          Show {customers.length - 3} more
        </button>
      )}
    </>
  )
}

function CustomerResult({ customer }: { customer: CustomerCard }) {
  return (
    <div className="result-card">
      <Link to={`/customers/${customer.id}`} className="result-title">
        <User size={16} aria-hidden="true" />
        {customer.name}
      </Link>
      {customer.address && <span className="muted">{customer.address}</span>}
      <div className="result-actions">
        <a href={`tel:${customer.phone}`} className="button call" aria-label={`Call ${customer.name}`}>
          <Phone size={16} aria-hidden="true" />
          {formatPhone(customer.phone)}
        </a>
        {customer.address && (
          <a
            href={directionsUrl(customer.address)}
            target="_blank"
            rel="noreferrer"
            className="button go"
            aria-label={`Directions to ${customer.name}`}
          >
            <NavigationArrow size={16} aria-hidden="true" />
            Navigate
          </a>
        )}
      </div>
    </div>
  )
}
