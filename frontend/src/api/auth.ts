import { postJson } from './client.ts'
import { session } from './session.ts'
import type { AuthResponse } from './types.ts'

export async function login(email: string, password: string): Promise<void> {
  session.set(await postJson<AuthResponse>('/api/auth/login', { email, password }))
}

export async function register(email: string, password: string, displayName: string): Promise<void> {
  const trimmedName = displayName.trim()
  session.set(
    await postJson<AuthResponse>('/api/auth/register', {
      email,
      password,
      displayName: trimmedName || undefined,
    }),
  )
}

export function logout(): void {
  session.clear()
}
