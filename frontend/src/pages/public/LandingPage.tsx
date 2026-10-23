import { ArrowDown, BellRinging, CalendarCheck, ChatCircleDots, ClipboardText, Code, DeviceMobile, ShieldCheck } from '@phosphor-icons/react'
import { Link } from 'react-router'

// The public front page at "/" for visitors who aren't logged in. Everything on it is
// true of the app as built: the screenshots are real screens (with made-up demo data),
// and there are no invented user counts, ratings or prices.

const GITHUB_URL = 'https://github.com/NaturalTC/ServiceLog'

const FEATURES = [
  {
    icon: CalendarCheck,
    title: 'My day',
    text: "Today's jobs in order. One tap to call the customer, one tap for turn-by-turn directions.",
  },
  {
    icon: ClipboardText,
    title: 'Every unit, every visit',
    text: 'Each customer’s equipment by address, with model, serial number, age and full service history.',
  },
  {
    icon: ChatCircleDots,
    title: 'Describe the visit, AI fills the form',
    text: 'Type or dictate what you did. AI drafts the service record for you to check before saving. Customer details never leave the app.',
  },
  {
    icon: BellRinging,
    title: 'Who’s due for a tune-up',
    text: 'Heating in the fall, cooling in the spring. Send a friendly reminder email with one tap; customers can unsubscribe.',
  },
  {
    icon: ShieldCheck,
    title: 'Warranty registration',
    text: 'Registration and warranty end dates on every unit, and a list of installs still to register.',
  },
  {
    icon: DeviceMobile,
    title: 'On your home screen',
    text: 'Installs like an app from the browser, with no app store. Works on iPhone and Android.',
  },
]

const STEPS = [
  { title: 'Open My day', text: 'Your jobs in order, with the customer’s number and directions one tap away.' },
  {
    title: 'Finish the job',
    text: 'Tick the units you worked on and describe the visit. The service history fills itself in.',
  },
  {
    title: 'Customers come back',
    text: 'When a tune-up is due, the app lists who to call or email, longest overdue first.',
  },
]

const SCREENS = [
  { src: '/landing/my-day.webp', alt: "My day: today's jobs with Call, Navigate and Finish job buttons" },
  { src: '/landing/service-note.webp', alt: 'Add service record: a visit described in a few words, filled in by AI' },
  { src: '/landing/equipment.webp', alt: "A boiler's page with its last tune-up and service history" },
  { src: '/landing/tune-ups.webp', alt: 'Tune-ups due: customers due for a seasonal tune-up with call and email buttons' },
]

export function LandingPage() {
  return (
    <div className="landing">
      <header className="landing-header">
        <span className="landing-brand">
          <img src="/icons/icon-192.png" alt="" width={28} height={28} />
          ServiceLog
        </span>
        <nav className="landing-nav" aria-label="Page">
          <a href="#features">Features</a>
          <a href="#how-it-works">How it works</a>
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">
            GitHub
          </a>
        </nav>
        <Link to="/login" className="button dark">
          Log in
        </Link>
      </header>

      <section className="landing-hero">
        <p className="eyebrow">
          ServiceLog <span>· Built with a one-person HVAC shop in western Massachusetts</span>
        </p>
        <h1>
          Every unit. Every visit.
          <br />
          <span>Every tune-up.</span>
        </h1>
        <p className="lead">
          Each customer’s equipment and service history in your pocket, your day in order, and a list of who’s due for
          a tune-up. Made for HVAC technicians who work on their own.
        </p>
        <div className="landing-cta">
          <a href="#how-it-works" className="button">
            See how it works
            <ArrowDown size={18} aria-hidden="true" />
          </a>
          <a href={GITHUB_URL} className="button dark" target="_blank" rel="noreferrer">
            <Code size={18} aria-hidden="true" />
            View on GitHub
          </a>
        </div>
        <p className="landing-stack muted">Spring Boot · PostgreSQL · React · TypeScript · Open source</p>
      </section>

      <section className="landing-showcase" aria-label="Screens from the app">
        <div className="landing-phones">
          {SCREENS.map((screen) => (
            <figure key={screen.src} className="phone">
              <img src={screen.src} alt={screen.alt} width={390} height={844} loading="lazy" />
            </figure>
          ))}
        </div>
        {/* Floating notes on wide screens, echoing what the screens show */}
        <div className="float-card float-left" aria-hidden="true">
          <span className="float-label">AI draft</span>
          <strong>LWCO probe replaced · CO 12 ppm</strong>
          <span className="float-meta">Follow-up: quote a new expansion tank</span>
        </div>
        <div className="float-card float-right" aria-hidden="true">
          <span className="float-label">Tune-up due</span>
          <strong>Heat pump · last done Apr 2024</strong>
          <span className="pill-ok">Reminder sent</span>
        </div>
      </section>
      <p className="landing-caption muted">Real screens from the app, with made-up demo customers.</p>

      <section className="landing-features" id="features">
        <p className="section-label">Features</p>
        <h2>What it does</h2>
        <ul>
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <li key={title}>
              <span className="feature-icon">
                <Icon size={20} aria-hidden="true" />
              </span>
              <h3>{title}</h3>
              <p>{text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="landing-steps" id="how-it-works">
        <p className="section-label">How it works</p>
        <h2>From the van to the next tune-up</h2>
        <ol>
          {STEPS.map((step) => (
            <li key={step.title}>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="landing-built">
        <div>
          <h2>Open source</h2>
          <p>
            Spring Boot and PostgreSQL behind a React and TypeScript app, planned in agile sprints with the shop’s
            owner and covered by automated tests. The AI feature shipped only after passing a measured accuracy bar. The
            code, plan and backlog are all on GitHub.
          </p>
          <a href={GITHUB_URL} className="button" target="_blank" rel="noreferrer">
            <Code size={18} aria-hidden="true" />
            View on GitHub
          </a>
        </div>
      </section>

      <footer className="landing-footer muted">
        ServiceLog · an open-source project ·{' '}
        <a href={GITHUB_URL} target="_blank" rel="noreferrer">
          GitHub
        </a>
      </footer>
    </div>
  )
}
