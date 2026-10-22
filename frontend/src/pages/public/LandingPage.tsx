import { Link } from 'react-router'

// The public front page at "/" for visitors who aren't logged in. Everything on it is
// true of the app as built: the screenshots are real screens (with made-up demo data),
// and there are no invented user counts, ratings or prices.

const GITHUB_URL = 'https://github.com/NaturalTC/ServiceLog'

const FEATURES = [
  {
    title: 'My day',
    text: "Today's jobs in order. One tap to call the customer, one tap for turn-by-turn directions.",
  },
  {
    title: 'Every unit, every visit',
    text: 'Each customer’s equipment by address, with model, serial number, age and full service history.',
  },
  {
    title: 'Finish a job, history fills itself',
    text: 'Tick the units you worked on and describe the work. A service record lands on each one.',
  },
  {
    title: 'Who’s due for a tune-up',
    text: 'Heating in the fall, cooling in the spring: the list of customers due, longest overdue first.',
  },
  {
    title: 'One-tap reminder emails',
    text: 'Send a friendly tune-up reminder from your list. Replies come to you; customers can unsubscribe.',
  },
  {
    title: 'On your home screen',
    text: 'Installs like an app from the browser, with no app store. Works on iPhone and Android.',
  },
]

const SCREENS = [
  { src: '/landing/my-day.webp', alt: "My day: today's jobs with Call, Navigate and Finish job buttons" },
  { src: '/landing/customer.webp', alt: "A customer's page with their address, equipment and Schedule job" },
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
        <Link to="/login" className="button secondary">
          Log in
        </Link>
      </header>

      <section className="landing-hero">
        <p className="eyebrow">Built with a one-person HVAC shop in western Massachusetts</p>
        <h1>
          Every unit. Every visit.
          <br />
          <span>Every tune-up.</span>
        </h1>
        <p className="lead">
          ServiceLog keeps each customer’s equipment and service history in your pocket, runs your day, and tells you
          who’s due for a tune-up. Made for HVAC technicians who work on their own.
        </p>
        <div className="landing-cta">
          <Link to="/login" className="button">
            Log in
          </Link>
          <a href={GITHUB_URL} className="button secondary" target="_blank" rel="noreferrer">
            View on GitHub
          </a>
        </div>
      </section>

      <section className="landing-phones" aria-label="Screens from the app">
        {SCREENS.map((screen) => (
          <figure key={screen.src} className="phone">
            <img src={screen.src} alt={screen.alt} width={390} height={844} loading="lazy" />
          </figure>
        ))}
      </section>
      <p className="landing-caption muted">Real screens from the app, with made-up demo customers.</p>

      <section className="landing-features">
        <h2>What it does</h2>
        <ul>
          {FEATURES.map((f) => (
            <li key={f.title}>
              <h3>{f.title}</h3>
              <p>{f.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="landing-built">
        <h2>Open source</h2>
        <p>
          Spring Boot and PostgreSQL behind a React and TypeScript app, planned in agile sprints with the shop’s owner
          and covered by automated tests. The code, plan and backlog are all on GitHub.
        </p>
        <a href={GITHUB_URL} className="button secondary" target="_blank" rel="noreferrer">
          View on GitHub
        </a>
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
