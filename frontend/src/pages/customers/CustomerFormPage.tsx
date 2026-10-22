import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { customersApi, type CustomerInput } from '../../api/customers'
import { formatPhone, isValidEmail, isValidState, isValidUsPhone, isValidZip } from '../../lib/format'
import { Loading } from '../../components/Loading'

// One form for both "new customer" (/customers/new) and "edit" (/customers/:id/edit).
// Built for speed on a phone: name gets focus, the phone field opens the number pad,
// and state defaults to MA.

interface PropertyDraft {
  key: number // React list key; stable even before the property is saved
  id: number | null
  street: string
  unit: string
  city: string
  state: string
  zip: string
  notes: string
}

interface Draft {
  name: string
  phone: string
  email: string
  notes: string
  properties: PropertyDraft[]
}

type Errors = Record<string, string>

let nextKey = 1
const emptyProperty = (): PropertyDraft => ({
  key: nextKey++,
  id: null,
  street: '',
  unit: '',
  city: '',
  state: 'MA',
  zip: '',
  notes: '',
})

function validate(d: Draft): Errors {
  const errors: Errors = {}
  if (!d.name.trim()) errors.name = 'Enter a name.'
  if (!isValidUsPhone(d.phone)) errors.phone = 'Enter a 10-digit phone number.'
  if (d.email.trim() && !isValidEmail(d.email.trim())) errors.email = 'Enter a valid email or leave it blank.'
  d.properties.forEach((p, i) => {
    if (!p.street.trim()) errors[`p${i}.street`] = 'Enter the street address.'
    if (!p.city.trim()) errors[`p${i}.city`] = 'Enter the city.'
    if (!isValidState(p.state)) errors[`p${i}.state`] = 'Use 2 letters, e.g. MA.'
    if (!isValidZip(p.zip)) errors[`p${i}.zip`] = 'Use 5 digits.'
  })
  return errors
}

const blankToNull = (s: string) => (s.trim() ? s.trim() : null)

const blankDraft = (): Draft => ({ name: '', phone: '', email: '', notes: '', properties: [emptyProperty()] })

// A customer prepared elsewhere (the assistant), as a form draft
function fromInput(input: Partial<CustomerInput>): Draft {
  const p = input.properties?.[0]
  return {
    name: input.name ?? '',
    phone: input.phone ?? '',
    email: input.email ?? '',
    notes: input.notes ?? '',
    properties: [
      {
        ...emptyProperty(),
        street: p?.street ?? '',
        unit: p?.unit ?? '',
        city: p?.city ?? '',
        state: p?.state ?? 'MA',
        zip: p?.zip ?? '',
      },
    ],
  }
}

function toInput(d: Draft): CustomerInput {
  return {
    name: d.name.trim(),
    phone: d.phone,
    email: blankToNull(d.email),
    notes: blankToNull(d.notes),
    properties: d.properties.map((p) => ({
      id: p.id,
      street: p.street.trim(),
      unit: blankToNull(p.unit),
      city: p.city.trim(),
      state: p.state.trim().toUpperCase(),
      zip: p.zip.trim(),
      notes: blankToNull(p.notes),
    })),
  }
}

export function CustomerFormPage() {
  const params = useParams()
  const editingId = params.id ? Number(params.id) : null
  const navigate = useNavigate()

  // Filled in by the assistant's "Edit" (S34) or "Describe it" (S35)
  const prefill = (useLocation().state as { prefill?: Partial<CustomerInput> } | null)?.prefill
  const [draft, setDraft] = useState<Draft | null>(editingId ? null : prefill ? fromInput(prefill) : blankDraft())
  const [errors, setErrors] = useState<Errors>({})
  const [submitted, setSubmitted] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // When editing, load the customer into the form
  useEffect(() => {
    if (!editingId) return
    customersApi
      .get(editingId)
      .then((c) =>
        setDraft({
          name: c.name,
          phone: formatPhone(c.phone),
          email: c.email ?? '',
          notes: c.notes ?? '',
          properties: c.properties.map((p) => ({
            key: nextKey++,
            id: p.id,
            street: p.street,
            unit: p.unit ?? '',
            city: p.city,
            state: p.state,
            zip: p.zip,
            notes: p.notes ?? '',
          })),
        }),
      )
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

  // After the first save attempt, re-check fields as the owner fixes them
  const update = (next: Draft) => {
    setDraft(next)
    if (submitted) setErrors(validate(next))
  }
  const setField = (field: keyof Omit<Draft, 'properties'>, value: string) => update({ ...draft, [field]: value })
  const setPropertyField = (index: number, field: keyof PropertyDraft, value: string) =>
    update({
      ...draft,
      properties: draft.properties.map((p, i) => (i === index ? { ...p, [field]: value } : p)),
    })
  const addProperty = () => update({ ...draft, properties: [...draft.properties, emptyProperty()] })
  const removeProperty = (index: number) =>
    update({ ...draft, properties: draft.properties.filter((_, i) => i !== index) })

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!draft) return
    setSubmitted(true)
    setServerError(null)
    const found = validate(draft)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSaving(true)
    try {
      const saved = editingId
        ? await customersApi.update(editingId, toInput(draft))
        : await customersApi.create(toInput(draft))
      navigate(`/customers/${saved.id}`, { replace: true })
    } catch (err) {
      setServerError(errorMessage(err))
      setSaving(false)
    }
  }

  const fieldError = (key: string) =>
    errors[key] && (
      <p className="error field-error" id={`${key}-error`}>
        {errors[key]}
      </p>
    )
  const describedBy = (key: string) => (errors[key] ? `${key}-error` : undefined)

  return (
    <main>
      <h1>{editingId ? 'Edit customer' : 'New customer'}</h1>
      <form onSubmit={handleSubmit} noValidate className="customer-form">
        <label htmlFor="name">Name</label>
        <input
          id="name"
          autoFocus={!editingId}
          autoComplete="off"
          autoCapitalize="words"
          value={draft.name}
          onChange={(e) => setField('name', e.target.value)}
          aria-invalid={!!errors.name}
          aria-describedby={describedBy('name')}
        />
        {fieldError('name')}

        <label htmlFor="phone">Phone</label>
        <input
          id="phone"
          type="tel"
          inputMode="tel"
          autoComplete="off"
          value={draft.phone}
          onChange={(e) => setField('phone', e.target.value)}
          onBlur={() => isValidUsPhone(draft.phone) && setField('phone', formatPhone(draft.phone))}
          aria-invalid={!!errors.phone}
          aria-describedby={describedBy('phone')}
        />
        {fieldError('phone')}

        <label htmlFor="email">
          Email <span className="optional">(optional)</span>
        </label>
        <input
          id="email"
          type="email"
          inputMode="email"
          autoComplete="off"
          value={draft.email}
          onChange={(e) => setField('email', e.target.value)}
          aria-invalid={!!errors.email}
          aria-describedby={describedBy('email')}
        />
        {fieldError('email')}

        {draft.properties.map((p, i) => (
          <fieldset key={p.key} className="property">
            <legend>{draft.properties.length > 1 ? `Address ${i + 1}` : 'Address'}</legend>

            <label htmlFor={`p${i}-street`}>Street</label>
            <input
              id={`p${i}-street`}
              autoComplete="off"
              value={p.street}
              onChange={(e) => setPropertyField(i, 'street', e.target.value)}
              aria-invalid={!!errors[`p${i}.street`]}
              aria-describedby={describedBy(`p${i}.street`)}
            />
            {fieldError(`p${i}.street`)}

            <label htmlFor={`p${i}-unit`}>
              Unit <span className="optional">(optional)</span>
            </label>
            <input
              id={`p${i}-unit`}
              autoComplete="off"
              value={p.unit}
              onChange={(e) => setPropertyField(i, 'unit', e.target.value)}
            />

            <label htmlFor={`p${i}-city`}>City</label>
            <input
              id={`p${i}-city`}
              autoComplete="off"
              autoCapitalize="words"
              value={p.city}
              onChange={(e) => setPropertyField(i, 'city', e.target.value)}
              aria-invalid={!!errors[`p${i}.city`]}
              aria-describedby={describedBy(`p${i}.city`)}
            />
            {fieldError(`p${i}.city`)}

            <div className="row">
              <div>
                <label htmlFor={`p${i}-state`}>State</label>
                <input
                  id={`p${i}-state`}
                  autoComplete="off"
                  autoCapitalize="characters"
                  maxLength={2}
                  value={p.state}
                  onChange={(e) => setPropertyField(i, 'state', e.target.value)}
                  aria-invalid={!!errors[`p${i}.state`]}
                  aria-describedby={describedBy(`p${i}.state`)}
                />
                {fieldError(`p${i}.state`)}
              </div>
              <div>
                <label htmlFor={`p${i}-zip`}>ZIP</label>
                <input
                  id={`p${i}-zip`}
                  inputMode="numeric"
                  autoComplete="off"
                  maxLength={10}
                  value={p.zip}
                  onChange={(e) => setPropertyField(i, 'zip', e.target.value)}
                  aria-invalid={!!errors[`p${i}.zip`]}
                  aria-describedby={describedBy(`p${i}.zip`)}
                />
                {fieldError(`p${i}.zip`)}
              </div>
            </div>

            <label htmlFor={`p${i}-notes`}>
              Notes <span className="optional">(gate code, dog, where the unit is…)</span>
            </label>
            <textarea
              id={`p${i}-notes`}
              rows={2}
              value={p.notes}
              onChange={(e) => setPropertyField(i, 'notes', e.target.value)}
            />

            {draft.properties.length > 1 && (
              <button type="button" className="secondary" onClick={() => removeProperty(i)}>
                Remove this address
              </button>
            )}
          </fieldset>
        ))}

        <button type="button" className="secondary" onClick={addProperty}>
          + Add another address
        </button>

        <label htmlFor="notes">
          Customer notes <span className="optional">(optional)</span>
        </label>
        <textarea id="notes" rows={3} value={draft.notes} onChange={(e) => setField('notes', e.target.value)} />

        {serverError && (
          <p className="error" role="alert">
            {serverError}
          </p>
        )}
        {submitted && Object.keys(errors).length > 0 && (
          <p className="error" role="alert">
            Fix the highlighted fields to save.
          </p>
        )}

        {/* Sticky so Save is always under the thumb */}
        <div className="sticky-actions">
          <Link to={editingId ? `/customers/${editingId}` : '/customers'} className="button secondary">
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
