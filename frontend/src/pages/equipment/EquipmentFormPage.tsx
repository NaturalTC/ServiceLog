import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { assistantApi, type EquipmentFill } from '../../api/assistant'
import { DescribeBox } from '../../components/DescribeBox'
import { equipmentApi, type EquipmentInput, type EquipmentType, type EquipmentTypeOption } from '../../api/equipment'
import { todayIso } from '../../lib/format'
import { Loading } from '../../components/Loading'

// One form for "add equipment" (/properties/:propertyId/equipment/new) and
// "edit" (/equipment/:id/edit). Only the type is required, so a unit can be logged
// fast on site and completed later.

// Suggestions only; any brand can be typed
const COMMON_BRANDS = [
  'Mitsubishi', 'Fujitsu', 'Daikin', 'Carrier', 'Trane', 'Lennox', 'Rheem', 'Goodman', 'York', 'American Standard',
  'Burnham', 'Weil-McLain', 'Peerless', 'Buderus', 'Navien', 'Beckett', 'Riello', 'Bradford White', 'A.O. Smith',
]

interface Draft {
  type: EquipmentType | ''
  brand: string
  model: string
  serialNumber: string
  installDate: string
  notes: string
  warrantyRegistered: boolean
  warrantyRegisteredOn: string
  warrantyEndsOn: string
  extendedWarrantyEndsOn: string
}

const BLANK: Draft = {
  type: '',
  brand: '',
  model: '',
  serialNumber: '',
  installDate: '',
  notes: '',
  warrantyRegistered: false,
  warrantyRegisteredOn: '',
  warrantyEndsOn: '',
  extendedWarrantyEndsOn: '',
}

const blankToNull = (s: string) => (s.trim() ? s.trim() : null)

export function EquipmentFormPage() {
  const params = useParams()
  const editingId = params.id ? Number(params.id) : null
  const propertyId = params.propertyId ? Number(params.propertyId) : null
  // Where Cancel goes when adding: back to the customer the owner came from
  const customerId = useSearchParams()[0].get('customerId')
  const navigate = useNavigate()

  const [types, setTypes] = useState<EquipmentTypeOption[]>([])
  const [draft, setDraft] = useState<Draft | null>(editingId ? null : BLANK)
  const [location, setLocation] = useState<{ customerId: number; customerName: string; address: string } | null>(
    null,
  )
  const [typeError, setTypeError] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    equipmentApi
      .types()
      .then(setTypes)
      .catch((err) => setServerError(errorMessage(err)))
  }, [])

  useEffect(() => {
    if (!editingId) return
    equipmentApi
      .get(editingId)
      .then(({ equipment: e, customerId, customerName, address }) => {
        setDraft({
          type: e.type,
          brand: e.brand ?? '',
          model: e.model ?? '',
          serialNumber: e.serialNumber ?? '',
          installDate: e.installDate ?? '',
          notes: e.notes ?? '',
          warrantyRegistered: e.warrantyRegistered,
          warrantyRegisteredOn: e.warrantyRegisteredOn ?? '',
          warrantyEndsOn: e.warrantyEndsOn ?? '',
          extendedWarrantyEndsOn: e.extendedWarrantyEndsOn ?? '',
        })
        setLocation({ customerId, customerName, address })
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
      <Loading />
    )
  }

  const set = (field: keyof Draft, value: string | boolean) => {
    setDraft({ ...draft, [field]: value })
    if (field === 'type' && value) setTypeError(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!draft) return
    setServerError(null)
    if (!draft.type) {
      setTypeError(true)
      return
    }
    const input: EquipmentInput = {
      type: draft.type,
      brand: blankToNull(draft.brand),
      model: blankToNull(draft.model),
      serialNumber: blankToNull(draft.serialNumber),
      installDate: draft.installDate || null,
      notes: blankToNull(draft.notes),
      warrantyRegistered: draft.warrantyRegistered,
      warrantyRegisteredOn: draft.warrantyRegistered ? draft.warrantyRegisteredOn || null : null,
      warrantyEndsOn: draft.warrantyEndsOn || null,
      extendedWarrantyEndsOn: draft.extendedWarrantyEndsOn || null,
    }
    setSaving(true)
    try {
      const saved = editingId
        ? await equipmentApi.update(editingId, input)
        : await equipmentApi.create(propertyId!, input)
      navigate(`/equipment/${saved.equipment.id}`, { replace: true })
    } catch (err) {
      setServerError(errorMessage(err))
      setSaving(false)
    }
  }

  const cancelTo = editingId ? `/equipment/${editingId}` : customerId ? `/customers/${customerId}` : '/customers'

  return (
    <main>
      <h1>{editingId ? 'Edit equipment' : 'Add equipment'}</h1>
      {location && <p className="muted">{location.address}</p>}

      {!editingId && (
        <DescribeBox
          placeholder="e.g. Mitsubishi condenser MXZ-3C24NA, serial 54A01234, installed 2023, side of the garage"
          fill={async (text) => {
            const f = await assistantApi.fill<EquipmentFill>('equipment', text)
            setDraft((d) =>
              d && {
                ...d,
                type: (f.type as EquipmentType | null) ?? d.type,
                brand: f.brand ?? d.brand,
                model: f.model ?? d.model,
                serialNumber: f.serialNumber ?? d.serialNumber,
                installDate: f.installDate ?? d.installDate,
                notes: f.notes ?? d.notes,
              },
            )
            if (f.type) setTypeError(false)
          }}
        />
      )}

      <form onSubmit={handleSubmit} noValidate className="customer-form">
        <label htmlFor="type">Type</label>
        <select
          id="type"
          value={draft.type}
          onChange={(e) => set('type', e.target.value)}
          aria-invalid={typeError}
          aria-describedby={typeError ? 'type-error' : undefined}
        >
          <option value="" disabled>
            Choose a type…
          </option>
          {types.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
        {typeError && (
          <p className="error field-error" id="type-error">
            Choose what kind of equipment this is.
          </p>
        )}

        <label htmlFor="brand">
          Brand <span className="optional">(optional)</span>
        </label>
        <input
          id="brand"
          list="brand-suggestions"
          autoComplete="off"
          autoCapitalize="words"
          value={draft.brand}
          onChange={(e) => set('brand', e.target.value)}
        />
        <datalist id="brand-suggestions">
          {COMMON_BRANDS.map((b) => (
            <option key={b} value={b} />
          ))}
        </datalist>

        <label htmlFor="model">
          Model <span className="optional">(optional)</span>
        </label>
        <input
          id="model"
          autoComplete="off"
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          value={draft.model}
          onChange={(e) => set('model', e.target.value)}
        />

        <label htmlFor="serial">
          Serial number <span className="optional">(optional; needed for warranty)</span>
        </label>
        <input
          id="serial"
          autoComplete="off"
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          value={draft.serialNumber}
          onChange={(e) => set('serialNumber', e.target.value)}
        />

        <label htmlFor="install-date">
          Install date <span className="optional">(optional)</span>
        </label>
        <input
          id="install-date"
          type="date"
          max={todayIso()}
          value={draft.installDate}
          onChange={(e) => set('installDate', e.target.value)}
        />

        <fieldset className="property">
          <legend>Warranty</legend>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={draft.warrantyRegistered}
              onChange={(e) => {
                const registered = e.target.checked
                // Ticking it fills in today; the date can still be changed
                setDraft({
                  ...draft,
                  warrantyRegistered: registered,
                  warrantyRegisteredOn: registered ? draft.warrantyRegisteredOn || todayIso() : '',
                })
              }}
            />
            Registered with the manufacturer
          </label>
          {draft.warrantyRegistered && (
            <>
              <label htmlFor="registered-on">
                Registration date <span className="optional">(optional)</span>
              </label>
              <input
                id="registered-on"
                type="date"
                min={draft.installDate || undefined}
                max={todayIso()}
                value={draft.warrantyRegisteredOn}
                onChange={(e) => set('warrantyRegisteredOn', e.target.value)}
              />
            </>
          )}
          <label htmlFor="warranty-ends">
            Warranty ends <span className="optional">(optional)</span>
          </label>
          <input
            id="warranty-ends"
            type="date"
            min={draft.installDate || undefined}
            value={draft.warrantyEndsOn}
            onChange={(e) => set('warrantyEndsOn', e.target.value)}
          />
          <label htmlFor="extended-ends">
            Extended warranty ends <span className="optional">(optional)</span>
          </label>
          <input
            id="extended-ends"
            type="date"
            min={draft.warrantyEndsOn || draft.installDate || undefined}
            value={draft.extendedWarrantyEndsOn}
            onChange={(e) => set('extendedWarrantyEndsOn', e.target.value)}
          />
        </fieldset>

        <label htmlFor="notes">
          Notes <span className="optional">(location, filter size, quirks…)</span>
        </label>
        <textarea id="notes" rows={3} value={draft.notes} onChange={(e) => set('notes', e.target.value)} />

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
