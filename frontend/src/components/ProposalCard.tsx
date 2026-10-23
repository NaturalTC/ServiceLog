import { Briefcase, CheckCircle, ClipboardText, PencilSimple, UserPlus } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type { Proposal } from '../api/assistant'
import { errorMessage } from '../api/client'
import { customersApi, type CustomerInput } from '../api/customers'
import { jobsApi, overlappingJobs, type Job, type JobInput } from '../api/jobs'
import { serviceRecordsApi, type ServiceRecordInput } from '../api/serviceRecords'
import { formatDate, formatTime } from '../lib/format'

// Something the assistant prepared (S34). Confirm saves it through the app's normal checks
// (e.g. double-booking); Edit opens the normal form, filled in. Nothing is saved before.
const ICONS = { JOB: Briefcase, CUSTOMER: UserPlus, SERVICE_RECORD: ClipboardText }

export function ProposalCard({ proposal, onDone }: { proposal: Proposal; onDone: (outcome: Proposal['outcome']) => void }) {
  const navigate = useNavigate()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [clash, setClash] = useState<Job[] | null>(null)
  const Icon = ICONS[proposal.type]
  const incomplete = proposal.missing.length > 0

  async function confirm(allowOverlap = false) {
    setSaving(true)
    setError(null)
    try {
      if (proposal.type === 'JOB') {
        const job = await jobsApi.create({ ...(proposal.payload as unknown as JobInput), allowOverlap })
        onDone({ label: `Booked: ${job.typeLabel}, ${formatDate(job.date)} at ${formatTime(job.startTime)}`, link: `/jobs/${job.id}` })
      } else if (proposal.type === 'CUSTOMER') {
        const customer = await customersApi.create(proposal.payload as unknown as CustomerInput)
        onDone({ label: `Added ${customer.name}`, link: `/customers/${customer.id}` })
      } else {
        const saved = await serviceRecordsApi.create(
          proposal.equipmentId!,
          proposal.payload as unknown as ServiceRecordInput,
        )
        onDone({ label: `Saved: ${saved.record.kindLabel} on ${saved.equipmentLabel}`, link: `/service-records/${saved.record.id}` })
      }
    } catch (err) {
      const overlaps = overlappingJobs(err)
      if (overlaps) setClash(overlaps)
      else setError(errorMessage(err))
    }
    setSaving(false)
  }

  function edit() {
    const state = { prefill: proposal.payload }
    if (proposal.type === 'JOB') {
      const p = proposal.payload as unknown as JobInput
      navigate(`/jobs/new?customerId=${proposal.customerId}&propertyId=${p.propertyId}&type=${p.type}`, { state })
    } else if (proposal.type === 'CUSTOMER') {
      navigate('/customers/new', { state })
    } else {
      navigate(`/equipment/${proposal.equipmentId}/service-records/new`, { state })
    }
  }

  if (proposal.outcome) {
    return (
      <Link to={proposal.outcome.link} className="proposal done">
        <CheckCircle size={18} aria-hidden="true" />
        <span>{proposal.outcome.label}</span>
      </Link>
    )
  }

  return (
    <div className="proposal">
      <div className="proposal-head">
        <span className="proposal-icon" aria-hidden="true">
          <Icon size={18} />
        </span>
        <div>
          <span className="section-label">Ready to confirm</span>
          <strong>{proposal.title}</strong>
        </div>
      </div>
      <dl>
        {proposal.details.map((d) => (
          <div key={d.label}>
            <dt>{d.label}</dt>
            <dd>{d.value}</dd>
          </div>
        ))}
      </dl>
      {incomplete && <p className="follow-up-tag">Still needed: {proposal.missing.join(', ')}. Tap Edit to add.</p>}
      {clash && (
        <div className="overlap" role="alert">
          <strong>This overlaps with:</strong>
          <ul>
            {clash.map((j) => (
              <li key={j.id}>
                {formatTime(j.startTime)} · {j.customerName} ({j.typeLabel})
              </li>
            ))}
          </ul>
          <button type="button" className="secondary" onClick={() => confirm(true)} disabled={saving}>
            Book anyway
          </button>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="proposal-actions">
        <button type="button" className="secondary" onClick={edit}>
          <PencilSimple size={16} aria-hidden="true" />
          Edit
        </button>
        {!incomplete && !clash && (
          <button type="button" onClick={() => confirm()} disabled={saving}>
            <CheckCircle size={16} aria-hidden="true" />
            {saving ? 'Saving…' : 'Confirm'}
          </button>
        )}
      </div>
    </div>
  )
}
