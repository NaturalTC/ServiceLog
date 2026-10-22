import { ApiError, apiFetch } from './client'

export type JobType = 'INSTALL' | 'SERVICE_CALL' | 'TUNE_UP' | 'ESTIMATE_VISIT'
export type JobUrgency = 'NORMAL' | 'URGENT'
export type JobStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'

export interface Job {
  id: number
  date: string // YYYY-MM-DD, business time zone
  startTime: string // HH:MM:SS
  durationMinutes: number
  type: JobType
  typeLabel: string
  urgency: JobUrgency
  status: JobStatus
  notes: string | null
  propertyId: number
  address: string
  customerId: number
  customerName: string
  customerPhone: string
  completedAt: string | null
}

// Still on the calendar and changeable
export const isOpen = (job: Job) => job.status === 'SCHEDULED' || job.status === 'IN_PROGRESS'

export interface CompleteJobInput {
  equipmentIds: number[]
  workDone: string | null
  partsUsed: string | null
  notes: string | null
  followUpNeeded: boolean
  followUpNote: string | null
}

export interface JobInput {
  propertyId: number
  date: string
  startTime: string // HH:MM
  durationMinutes: number
  type: JobType
  urgency: JobUrgency
  notes: string | null
  allowOverlap?: boolean // set after the owner confirms a double booking
}

// "My day": the business's today and its jobs in time order
export interface Today {
  date: string
  jobs: Job[]
}

export interface JobTypeOption {
  value: JobType
  label: string
}

// A 409 from the API when a booking would overlap other jobs
export function overlappingJobs(err: unknown): Job[] | null {
  if (err instanceof ApiError && err.code === 'JOB_OVERLAP') {
    return (err.body as { conflicts: Job[] }).conflicts
  }
  return null
}

const json = (method: string, body: object): RequestInit => ({ method, body: JSON.stringify(body) })

export const jobsApi = {
  types: () => apiFetch<JobTypeOption[]>('/api/job-types'),
  today: () => apiFetch<Today>('/api/jobs/today'),
  scheduled: (from: string, to: string) => apiFetch<Job[]>(`/api/jobs?from=${from}&to=${to}`),
  upcomingForCustomer: (customerId: number) => apiFetch<Job[]>(`/api/customers/${customerId}/jobs`),
  get: (id: number) => apiFetch<Job>(`/api/jobs/${id}`),
  create: (input: JobInput) => apiFetch<Job>('/api/jobs', json('POST', input)),
  update: (id: number, input: JobInput) => apiFetch<Job>(`/api/jobs/${id}`, json('PUT', input)),
  cancel: (id: number) => apiFetch<Job>(`/api/jobs/${id}/cancel`, { method: 'POST' }),
  start: (id: number) => apiFetch<Job>(`/api/jobs/${id}/start`, { method: 'POST' }),
  // Marks the job done and creates a service record on each selected unit
  complete: (id: number, input: CompleteJobInput) =>
    apiFetch<{ job: Job; serviceRecordIds: number[] }>(`/api/jobs/${id}/complete`, json('POST', input)),
}
