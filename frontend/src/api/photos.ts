import { apiFetch } from './client'

// The links are signed and expire after an hour or two, so fetch the list again when needed
export interface Photo {
  id: number
  url: string
  thumbUrl: string
  width: number
  height: number
  createdAt: string
}

export const photosApi = {
  list: (recordId: number) => apiFetch<Photo[]>(`/api/service-records/${recordId}/photos`),
  upload: (recordId: number, images: Blob[]) => {
    const form = new FormData()
    images.forEach((image, i) => form.append('files', image, `photo-${i + 1}.jpg`))
    return apiFetch<Photo[]>(`/api/service-records/${recordId}/photos`, { method: 'POST', body: form })
  },
  remove: (id: number) => apiFetch<void>(`/api/photos/${id}`, { method: 'DELETE' }),
}
