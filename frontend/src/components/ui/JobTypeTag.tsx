import { ClipboardText, Package, Toolbox, Wrench, type Icon } from '@phosphor-icons/react'
import type { JobType } from '../../api/jobs'
import { jobTypeClass } from '../../lib/jobTypes'

// Each kind of job has its own colour and icon, the same everywhere (list, My day, calendar)
const ICONS: Record<JobType, Icon> = {
  TUNE_UP: Wrench,
  SERVICE_CALL: Toolbox,
  INSTALL: Package,
  ESTIMATE_VISIT: ClipboardText,
}

export function JobTypeTag({ type, label, large = false }: { type: JobType; label: string; large?: boolean }) {
  const Glyph = ICONS[type] ?? Wrench
  return (
    <span className={`job-type ${jobTypeClass(type)}${large ? ' large' : ''}`}>
      <Glyph size={large ? 18 : 14} weight="bold" aria-hidden="true" />
      {label}
    </span>
  )
}
