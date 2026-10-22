import type { ReactNode } from 'react'

// The assistant's answers: plain sentences, "- " bullets and **bold**. Rendered as React
// elements (never as HTML), so nothing in an answer can inject markup.
export function RichText({ text }: { text: string }) {
  const blocks: ReactNode[] = []
  let bullets: string[] = []
  const flush = () => {
    if (bullets.length > 0) {
      blocks.push(
        <ul key={`ul-${blocks.length}`}>
          {bullets.map((b, i) => (
            <li key={i}>{inline(b)}</li>
          ))}
        </ul>,
      )
      bullets = []
    }
  }
  for (const line of text.split('\n')) {
    const bullet = line.match(/^\s*[-•*]\s+(.*)$/)
    if (bullet) {
      bullets.push(bullet[1])
    } else {
      flush()
      if (line.trim()) blocks.push(<p key={`p-${blocks.length}`}>{inline(line)}</p>)
    }
  }
  flush()
  return <>{blocks}</>
}

function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? <strong key={i}>{part.slice(2, -2)}</strong> : part,
  )
}
