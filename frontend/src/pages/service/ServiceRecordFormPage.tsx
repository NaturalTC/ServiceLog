import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { equipmentApi } from '../../api/equipment'
import {
  serviceRecordsApi,
  type NoteDraft,
  type ServiceKind,
  type ServiceKindOption,
  type ServiceRecordInput,
} from '../../api/serviceRecords'
import { todayIso } from '../../lib/format'

// One form for "add service record" (/equipment/:equipmentId/service-records/new) and
// "edit" (/service-records/:id/edit). Built to fill in on site: the date is already today,
// the kind is one tap, and "Work done" is the only required field.

interface Draft {
  serviceDate: string
  kind: ServiceKind
  workDone: string
  partsUsed: string
  notes: string
  followUpNeeded: boolean
  followUpNote: string
}

const blankToNull = (s: string) => (s.trim() ? s.trim() : null)

// "CO 12 ppm; Draft -0.02 in wc": readings go in the record's notes
const formatReadings = (readings: NoteDraft['readings']) =>
  readings.map((r) => `${r.name} ${r.value}${r.unit ? ` ${r.unit}` : ''}`).join('; ')

export function ServiceRecordFormPage() {
  const params = useParams()
  const editingId = params.id ? Number(params.id) : null
  const [equipmentId, setEquipmentId] = useState<number | null>(
    params.equipmentId ? Number(params.equipmentId) : null,
  )
  const navigate = useNavigate()

  const [kinds, setKinds] = useState<ServiceKindOption[]>([])
  const [context, setContext] = useState<{ label: string; address: string } | null>(null)
  const [draft, setDraft] = useState<Draft | null>(
    editingId
      ? null
      : {
          serviceDate: todayIso(),
          kind: 'TUNE_UP',
          workDone: '',
          partsUsed: '',
          notes: '',
          followUpNeeded: false,
          followUpNote: '',
        },
  )
  const [workDoneError, setWorkDoneError] = useState(false)
  // "Fill in with AI": the owner's own words in, a draft of the form out
  const [quickNote, setQuickNote] = useState('')
  const [drafting, setDrafting] = useState(false)
  const [aiMessage, setAiMessage] = useState<{ text: string; ok: boolean } | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    serviceRecordsApi
      .kinds()
      .then(setKinds)
      .catch((err) => setServerError(errorMessage(err)))
  }, [])

  // Adding: show which unit this is for
  useEffect(() => {
    if (editingId || !equipmentId) return
    equipmentApi
      .get(equipmentId)
      .then(({ equipment: e, address }) =>
        setContext({ label: [e.typeLabel, e.brand].filter(Boolean).join(' · '), address }),
      )
      .catch((err) => setServerError(errorMessage(err)))
  }, [editingId, equipmentId])

  // Editing: load the record
  useEffect(() => {
    if (!editingId) return
    serviceRecordsApi
      .get(editingId)
      .then(({ record: r, equipmentLabel, address }) => {
        setDraft({
          serviceDate: r.serviceDate,
          kind: r.kind,
          workDone: r.workDone,
          partsUsed: r.partsUsed ?? '',
          notes: r.notes ?? '',
          followUpNeeded: r.followUpNeeded,
          followUpNote: r.followUpNote ?? '',
        })
        setEquipmentId(r.equipmentId)
        setContext({ label: equipmentLabel, address })
      })
      .catch((err) => setServerError(errorMessage(err)))
  }, [editingId])

  if (!draft) {
    return serverError ? (
      <main>
        <p className="error" role="alert">
          {serverError}
        </p>
      </main>
    ) : (
      <p className="loading">Loading…</p>
    )
  }

  const set = <K extends keyof Draft>(field: K, value: Draft[K]) => {
    setDraft({ ...draft, [field]: value })
    if (field === 'workDone' && String(value).trim()) setWorkDoneError(false)
  }

  // Pre-fills the form from the note. Nothing is saved until the owner checks it and taps Save.
  async function fillWithAi() {
    if (!draft || !equipmentId || !quickNote.trim()) return
    setDrafting(true)
    setAiMessage(null)
    try {
      const ai = await serviceRecordsApi.draftFromNote(equipmentId, quickNote.trim())
      const readings = formatReadings(ai.readings)
      setDraft({
        ...draft,
        kind: ai.kind,
        workDone: ai.workDone,
        partsUsed: ai.partsUsed.join(', '),
        notes: [readings, draft.notes.trim()].filter(Boolean).join('\n'),
        followUpNeeded: ai.followUpNeeded,
        followUpNote: ai.followUpNote ?? '',
      })
      setWorkDoneError(false)
      setAiMessage({ text: 'Filled in by AI. Check every field before saving.', ok: true })
    } catch (err) {
      setAiMessage({ text: errorMessage(err), ok: false })
    }
    setDrafting(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!draft) return
    setServerError(null)
    if (!draft.workDone.trim()) {
      setWorkDoneError(true)
      return
    }
    const input: ServiceRecordInput = {
      serviceDate: draft.serviceDate || null,
      kind: draft.kind,
      workDone: draft.workDone.trim(),
      partsUsed: blankToNull(draft.partsUsed),
      notes: blankToNull(draft.notes),
      followUpNeeded: draft.followUpNeeded,
      followUpNote: draft.followUpNeeded ? blankToNull(draft.followUpNote) : null,
    }
    setSaving(true)
    try {
      const saved = editingId
        ? await serviceRecordsApi.update(editingId, input)
        : await serviceRecordsApi.create(equipmentId!, input)
      navigate(`/service-records/${saved.record.id}`, { replace: true })
    } catch (err) {
      setServerError(errorMessage(err))
      setSaving(false)
    }
  }

  const cancelTo = editingId ? `/service-records/${editingId}` : `/equipment/${equipmentId}`

  return (
    <main>
      <h1>{editingId ? 'Edit service record' : 'Add service record'}</h1>
      {context && (
        <p className="muted">
          {context.label} · {context.address}
        </p>
      )}

      {!editingId && (
        <section className="ai-box" aria-labelledby="ai-title">
          <h2 id="ai-title">Describe the visit</h2>
          <p className="muted no-margin">
            Type or dictate it (tap the microphone on your keyboard) and AI fills in the form below. You check it before
            saving.
          </p>
          <label htmlFor="quick-note" className="visually-hidden">
            What you did
          </label>
          <textarea
            id="quick-note"
            rows={3}
            placeholder="e.g. annual on the burner, new nozzle and filter, CO 10"
            value={quickNote}
            onChange={(e) => setQuickNote(e.target.value)}
          />
          <button type="button" className="secondary" onClick={fillWithAi} disabled={drafting || !quickNote.trim()}>
            {drafting ? 'Filling in…' : 'Fill in with AI'}
          </button>
          {aiMessage && (
            <p className={aiMessage.ok ? 'notice no-margin' : 'error no-margin'} role="status">
              {aiMessage.text}
            </p>
          )}
        </section>
      )}

      <form onSubmit={handleSubmit} noValidate className="customer-form">
        <fieldset className="choice-group">
          <legend>Kind of visit</legend>
          <div className="chips">
            {kinds.map((k) => (
              <label key={k.value} className={draft.kind === k.value ? 'chip selected' : 'chip'}>
                <input
                  type="radio"
                  name="kind"
                  value={k.value}
                  checked={draft.kind === k.value}
                  onChange={() => set('kind', k.value)}
                />
                {k.label}
              </label>
            ))}
          </div>
        </fieldset>

        <label htmlFor="service-date">Date</label>
        <input
          id="service-date"
          type="date"
          max={todayIso()}
          value={draft.serviceDate}
          onChange={(e) => set('serviceDate', e.target.value)}
        />

        <label htmlFor="work-done">Work done</label>
        <textarea
          id="work-done"
          rows={4}
          value={draft.workDone}
          onChange={(e) => set('workDone', e.target.value)}
          aria-invalid={workDoneError}
          aria-describedby={workDoneError ? 'work-done-error' : undefined}
        />
        {workDoneError && (
          <p className="error field-error" id="work-done-error">
            Describe the work done.
          </p>
        )}

        <label htmlFor="parts-used">
          Parts used <span className="optional">(optional)</span>
        </label>
        <textarea
          id="parts-used"
          rows={2}
          value={draft.partsUsed}
          onChange={(e) => set('partsUsed', e.target.value)}
        />

        <label htmlFor="notes">
          Notes <span className="optional">(optional)</span>
        </label>
        <textarea id="notes" rows={3} value={draft.notes} onChange={(e) => set('notes', e.target.value)} />

        <label className="checkbox">
          <input
            type="checkbox"
            checked={draft.followUpNeeded}
            onChange={(e) => set('followUpNeeded', e.target.checked)}
          />
          Follow-up needed
        </label>
        {draft.followUpNeeded && (
          <>
            <label htmlFor="follow-up-note">
              What's needed? <span className="optional">(optional)</span>
            </label>
            <input
              id="follow-up-note"
              autoComplete="off"
              placeholder="e.g. Quote a new heat exchanger"
              value={draft.followUpNote}
              onChange={(e) => set('followUpNote', e.target.value)}
            />
          </>
        )}

        {serverError && (
          <p className="error" role="alert">
            {serverError}
          </p>
        )}

        <div className="sticky-actions">
          <Link to={cancelTo} className="button secondary">
            Cancel
          </Link>
          <button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </main>
  )
}
