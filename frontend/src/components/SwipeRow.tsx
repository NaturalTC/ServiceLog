import { NavigationArrow, Phone } from '@phosphor-icons/react'
import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'motion/react'
import { useRef, type ReactNode } from 'react'

// A row you can swipe: right to call, left to get directions. A shortcut only: the
// same actions are always on screen as buttons too. Vertical scrolling still works
// (the drag only takes over once the finger moves sideways).
const TRIGGER = 88 // px of travel that counts as a swipe

export function SwipeRow({
  children,
  phone,
  directions,
  label,
}: {
  children: ReactNode
  phone: string
  directions: string
  label: string // who or where, for the hint text
}) {
  const x = useMotionValue(0)
  const reduced = useReducedMotion()
  const dragged = useRef(false)
  const callOpacity = useTransform(x, [0, TRIGGER], [0, 1])
  const driveOpacity = useTransform(x, [-TRIGGER, 0], [1, 0])

  function settle() {
    animate(x, 0, reduced ? { duration: 0 } : { type: 'spring', stiffness: 500, damping: 40 })
  }

  return (
    <div className="swipe">
      <div className="swipe-under" aria-hidden="true">
        <motion.span className="swipe-call" style={{ opacity: callOpacity }}>
          <Phone size={22} weight="bold" />
          Call
        </motion.span>
        <motion.span className="swipe-drive" style={{ opacity: driveOpacity }}>
          Drive
          <NavigationArrow size={22} weight="bold" />
        </motion.span>
      </div>
      <motion.div
        className="swipe-top"
        style={{ x }}
        drag="x"
        dragDirectionLock
        dragConstraints={{ left: 0, right: 0 }}
        dragElastic={0.6}
        onDragStart={() => (dragged.current = true)}
        onDragEnd={(_, info) => {
          if (info.offset.x > TRIGGER) window.location.href = `tel:${phone}`
          else if (info.offset.x < -TRIGGER) window.open(directions, '_blank', 'noreferrer')
          settle()
          // Let the click that ends a drag through as a no-op, not a tap on the row
          setTimeout(() => (dragged.current = false), 0)
        }}
        onClickCapture={(e) => {
          if (dragged.current) {
            e.preventDefault()
            e.stopPropagation()
          }
        }}
        aria-description={`Swipe right to call or left for directions to ${label}`}
      >
        {children}
      </motion.div>
    </div>
  )
}
