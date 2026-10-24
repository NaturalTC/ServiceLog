import type { Job } from '../../api/jobs'
import { formatDuration, formatTime } from '../../lib/format'
import { Pill } from '../../components/ui/Pill'
import { Row } from '../../components/ui/Row'
import { JobTypeTag } from '../../components/ui/JobTypeTag'

// One day's jobs as ruled rows: time and customer, then type, length and town
export function JobRows({ jobs }: { jobs: Job[] }) {
  return (
    <ul className="rows">
      {jobs.map((job) => (
        <li key={job.id}>
          <Row
            to={`/jobs/${job.id}`}
            title={
              <>
                <span className="time">{formatTime(job.startTime)}</span> · {job.customerName}
              </>
            }
            sub={
              <>
                <JobTypeTag type={job.type} label={job.typeLabel} /> {formatDuration(job.durationMinutes)} ·{' '}
                {townOf(job.address)}
              </>
            }
            end={
              <>
                {job.status === 'DONE' && <Pill tone="done">Done</Pill>}
                {job.status === 'IN_PROGRESS' && <Pill tone="now">In progress</Pill>}
                {job.urgency === 'URGENT' && job.status !== 'DONE' && <Pill tone="urgent">Urgent</Pill>}
              </>
            }
          />
        </li>
      ))}
    </ul>
  )
}

// "250 State St, Apt 5B, Springfield, MA 01103" -> "Springfield"
function townOf(address: string): string {
  const parts = address.split(', ')
  return parts.length >= 2 ? parts[parts.length - 2] : address
}
