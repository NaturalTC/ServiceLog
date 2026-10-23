import { Camera, Trash, X } from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react'
import { errorMessage } from '../api/client'
import { photosApi, type Photo } from '../api/photos'
import { shrinkPhoto } from '../lib/photos'

// "Add photos": on a phone this offers the camera or the photo library
export function AddPhotosButton({ onPick, busy }: { onPick: (files: File[]) => void; busy?: boolean }) {
  function picked(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = '' // so picking the same photo again still counts
    if (files.length > 0) onPick(files)
  }
  return (
    <label className={`button secondary file-button${busy ? ' disabled' : ''}`}>
      <Camera size={18} aria-hidden="true" />
      {busy ? 'Uploading…' : 'Add photos'}
      <input type="file" accept="image/jpeg,image/png,image/*" multiple onChange={picked} disabled={busy} />
    </label>
  )
}

// The photos on a saved service record: thumbnails, add more, view full size, delete
export function PhotoGallery({ recordId }: { recordId: number }) {
  const [photos, setPhotos] = useState<Photo[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [open, setOpen] = useState<Photo | null>(null)
  const dialog = useRef<HTMLDialogElement>(null)

  const load = useCallback(() => {
    photosApi
      .list(recordId)
      .then(setPhotos)
      .catch((err) => setError(errorMessage(err)))
  }, [recordId])

  useEffect(load, [load])

  useEffect(() => {
    if (open) dialog.current?.showModal()
    else dialog.current?.close()
  }, [open])

  async function add(files: File[]) {
    setUploading(true)
    setError(null)
    try {
      const added = await photosApi.upload(recordId, await Promise.all(files.map(shrinkPhoto)))
      setPhotos((current) => [...(current ?? []), ...added])
    } catch (err) {
      setError(errorMessage(err))
    }
    setUploading(false)
  }

  async function remove(photo: Photo) {
    if (!window.confirm('Delete this photo?')) return
    try {
      await photosApi.remove(photo.id)
      setPhotos((current) => current?.filter((p) => p.id !== photo.id) ?? null)
      setOpen(null)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section aria-labelledby="photos-title">
      <h2 id="photos-title">Photos</h2>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {photos && photos.length > 0 && (
        <ul className="photo-grid">
          {photos.map((photo, i) => (
            <li key={photo.id}>
              <button type="button" className="photo-thumb" onClick={() => setOpen(photo)}>
                <img src={photo.thumbUrl} alt={`Photo ${i + 1} of ${photos.length}`} loading="lazy" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {photos?.length === 0 && <p className="muted">No photos yet.</p>}
      <AddPhotosButton onPick={add} busy={uploading} />

      <dialog ref={dialog} className="photo-viewer" onClose={() => setOpen(null)} aria-label="Photo">
        {open && (
          <>
            <img src={open.url} alt="" width={open.width} height={open.height} />
            <div className="photo-viewer-actions">
              <button type="button" className="secondary danger" onClick={() => remove(open)}>
                <Trash size={18} aria-hidden="true" />
                Delete
              </button>
              <button type="button" className="dark" onClick={() => setOpen(null)}>
                <X size={18} aria-hidden="true" />
                Close
              </button>
            </div>
          </>
        )}
      </dialog>
    </section>
  )
}

// Photos picked on the "Add service record" form, before the record exists: previews
// only; the form uploads them right after saving
export function PendingPhotos({ files, onChange }: { files: File[]; onChange: (files: File[]) => void }) {
  const [previews, setPreviews] = useState<string[]>([])

  useEffect(() => {
    const urls = files.map((f) => URL.createObjectURL(f))
    setPreviews(urls)
    return () => urls.forEach((u) => URL.revokeObjectURL(u))
  }, [files])

  return (
    <div className="pending-photos">
      <span className="label-text">
        Photos <span className="optional">(optional)</span>
      </span>
      {files.length > 0 && (
        <ul className="photo-grid">
          {previews.map((src, i) => (
            <li key={src}>
              <img src={src} alt={`Photo ${i + 1} to add`} className="photo-thumb-image" />
              <button
                type="button"
                className="photo-remove"
                aria-label={`Remove photo ${i + 1}`}
                onClick={() => onChange(files.filter((_, j) => j !== i))}
              >
                <X size={16} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
      <AddPhotosButton onPick={(picked) => onChange([...files, ...picked])} />
    </div>
  )
}
