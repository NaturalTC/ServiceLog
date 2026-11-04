import { DotsThree, type Icon } from '@phosphor-icons/react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Drawer } from 'vaul'

// The things you only do now and then (Reschedule, Cancel, Undo start), out of the way
// behind one "More" button, in a sheet with big rows a thumb can reach
export interface MoreItem {
  label: string
  icon?: Icon
  to?: string // go to a page
  onSelect?: () => void // or run something
  danger?: boolean
}

export function MoreMenu({ title, items }: { title: string; items: MoreItem[] }) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()

  function choose(item: MoreItem) {
    setOpen(false)
    if (item.to) navigate(item.to)
    else item.onSelect?.()
  }

  return (
    <Drawer.Root
      open={open}
      onOpenChange={(next) => {
        if (next) toast.dismiss()
        setOpen(next)
      }}
    >
      <Drawer.Trigger asChild>
        <button type="button" className="secondary more-button" aria-label="More actions">
          <DotsThree size={26} weight="bold" aria-hidden="true" />
        </button>
      </Drawer.Trigger>
      <Drawer.Portal>
        <Drawer.Overlay className="sheet-overlay" />
        <Drawer.Content className="sheet">
          <div className="sheet-handle" aria-hidden="true" />
          <Drawer.Title className="sheet-title">{title}</Drawer.Title>
          <Drawer.Description className="visually-hidden">More actions</Drawer.Description>
          <ul className="more-list">
            {items.map((item) => (
              <li key={item.label}>
                <button type="button" className={item.danger ? 'danger' : ''} onClick={() => choose(item)}>
                  {item.icon && <item.icon size={22} aria-hidden="true" />}
                  {item.label}
                </button>
              </li>
            ))}
          </ul>
          <button type="button" className="secondary" onClick={() => setOpen(false)}>
            Close
          </button>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  )
}
