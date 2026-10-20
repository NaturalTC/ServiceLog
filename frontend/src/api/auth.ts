import { apiFetch } from './client'

interface MessageResponse {
  message: string
}

const post = (path: string, body: object) =>
  apiFetch<MessageResponse>(path, { method: 'POST', body: JSON.stringify(body) })

export const authApi = {
  verifyEmail: (token: string) => post('/api/auth/verify-email', { token }),
  resendVerification: (email: string) => post('/api/auth/resend-verification', { email }),
  forgotPassword: (email: string) => post('/api/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    post('/api/auth/reset-password', { token, newPassword }),
}
