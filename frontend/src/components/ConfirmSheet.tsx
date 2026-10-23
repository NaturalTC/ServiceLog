import { useCallback, useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Drawer } from 'vaul'

// "Are you sure?" as a sheet that slides up from the bottom, where a thumb can reach the
// buttons (S41). Replaces the browser's confirm box. Swiping it down or tapping outside
// counts as Cancel, so nothing happens by accident.
export interface ConfirmOptions {
  title: string
  message?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  danger?: boolean // e.g. Delete, Cancel job, Log out with unsynced changes
}

// const [ask, sheet] = useConfirm(); render {sheet}; then `if (!(await ask({...}))) return`.
// `container` puts the sheet inside an open <dialog> (e.g. the photo viewer), which would
// otherwise sit on top of it.
export function useConfirm(container?: HTMLElement | null): [(options: ConfirmOptions) => Promise<boolean>, ReactNode] {
  const [options, setOptions] = useState<ConfirmOptions | null>(null)
  const [open, setOpen] = useState(false)
  const resolver = useRef<((ok: boolean) => void) | null>(null)

  const ask = useCallback((next: ConfirmOptions) => {
    resolver.current?.(false)
    toast.dismiss() // a toast would sit over the sheet's buttons
    setOptions(next)
    setOpen(true)
    return new Promise<boolean>((resolve) => (resolver.current = resolve))
  }, [])

  function settle(ok: boolean) {
    resolver.current?.(ok)
    resolver.current = null
    setOpen(false)
  }

  const sheet = options && (
    <Drawer.Root open={open} onOpenChange={(next) => !next && settle(false)}>
      <Drawer.Portal container={container}>
        <Drawer.Overlay className="sheet-overlay" />
        <Drawer.Content className="sheet">
          <div className="sheet-handle" aria-hidden="true" />
          <Drawer.Title className="sheet-title">{options.title}</Drawer.Title>
          {options.message ? (
            <Drawer.Description className="sheet-message">{options.message}</Drawer.Description>
          ) : (
            <Drawer.Description className="visually-hidden">Confirm or cancel</Drawer.Description>
          )}
          <div className="sheet-actions">
            <button type="button" className={options.danger ? 'sheet-danger' : ''} onClick={() => settle(true)}>
              {options.confirmLabel}
            </button>
            <button type="button" className="secondary" onClick={() => settle(false)}>
              {options.cancelLabel ?? 'Cancel'}
            </button>
          </div>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  )

  return [ask, sheet]
}
