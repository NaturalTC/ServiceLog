import type { JobType } from '../api/jobs'

// The class that gives each kind of job its colour (see .job-type and the month dots)
const CSS: Record<JobType, string> = {
  TUNE_UP: 'tune-up',
  SERVICE_CALL: 'service-call',
  INSTALL: 'install',
  ESTIMATE_VISIT: 'estimate',
}

export function jobTypeClass(type: JobType): string {
  return CSS[type] ?? ''
}
