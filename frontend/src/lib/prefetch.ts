import { apiFetch } from '../api/client'
import { connection } from './connection'
import type { Equipment } from '../api/equipment'
import type { Job } from '../api/jobs'

// Saves everything today's jobs need onto the phone while there's signal (S21): each job,
// its customer's page, and every unit's details and history at those addresses. Then the
// basement visit works even if the owner never opened those pages beforehand.

const MIN_INTERVAL_MS = 10 * 60_000 // don't redo it on every visit to My day
let lastRun = 0
let running = false

export async function saveTodayForOffline(jobs: Job[]) {
  if (running || Date.now() - lastRun < MIN_INTERVAL_MS) return
  running = true
  try {
    // Lists every form needs
    await fetchAll(['/api/service-kinds', '/api/equipment-types', '/api/job-types'])
    for (const customerId of new Set(jobs.map((j) => j.customerId))) {
      await fetchAll([
        `/api/customers/${customerId}`,
        `/api/customers/${customerId}/jobs`,
        `/api/customers/${customerId}/reminders`,
      ])
      const units = await apiFetch<Equipment[]>(`/api/customers/${customerId}/equipment`).catch(() => [])
      for (const unit of units) {
        await fetchAll([`/api/equipment/${unit.id}`, `/api/equipment/${unit.id}/service-records`])
      }
    }
    await fetchAll(jobs.map((j) => `/api/jobs/${j.id}`))
    // Only counts if it really reached the server (offline, it just re-read the saved copies)
    if (connection.get().online) lastRun = Date.now()
  } finally {
    running = false
  }
}

// One at a time, to stay gentle on a weak connection; failures are fine (it's a head start)
async function fetchAll(paths: string[]) {
  for (const path of paths) {
    await apiFetch(path).catch(() => {})
  }
}
