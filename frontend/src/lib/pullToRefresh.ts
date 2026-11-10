import { useEffect, useRef, useState } from 'react'

// Pull down at the top of a screen to load it again, as in every iPhone list. The page is
// remounted (its key changes), so it fetches fresh data the way it does when opened.
// Forms and the chat are left out: a remount there would lose what's been typed.
export const PULL_TO_REFRESH = 72 // px of pull (after the drag resistance) that triggers it
const MAX_PULL = 110

export function canPullToRefresh(path: string): boolean {
  return !/\/(new|edit|finish)$/.test(path) && path !== '/assistant' && path !== '/sync'
}

export function usePullToRefresh(enabled: boolean, onRefresh: () => void) {
  const [pull, setPull] = useState(0)
  const start = useRef<number | null>(null)
  const latest = useRef(0)
  const refresh = useRef(onRefresh)
  refresh.current = onRefresh

  useEffect(() => {
    if (!enabled) return
    const onStart = (e: TouchEvent) => {
      // Only from the very top, and not when a sheet or menu is open over the page
      const sheetOpen = document.querySelector('[data-vaul-drawer][data-state="open"]')
      start.current = window.scrollY <= 0 && !sheetOpen ? e.touches[0].clientY : null
    }
    const onMove = (e: TouchEvent) => {
      if (start.current === null) return
      const dy = e.touches[0].clientY - start.current
      if (dy <= 0 || window.scrollY > 0) {
        latest.current = 0
        setPull(0)
        return
      }
      // Resistance, so it feels like stretching rather than dragging
      latest.current = Math.min(MAX_PULL, dy * 0.5)
      setPull(latest.current)
    }
    const onEnd = () => {
      if (start.current !== null && latest.current >= PULL_TO_REFRESH) refresh.current()
      start.current = null
      latest.current = 0
      setPull(0)
    }
    window.addEventListener('touchstart', onStart, { passive: true })
    window.addEventListener('touchmove', onMove, { passive: true })
    window.addEventListener('touchend', onEnd)
    window.addEventListener('touchcancel', onEnd)
    return () => {
      window.removeEventListener('touchstart', onStart)
      window.removeEventListener('touchmove', onMove)
      window.removeEventListener('touchend', onEnd)
      window.removeEventListener('touchcancel', onEnd)
    }
  }, [enabled])

  return pull
}
