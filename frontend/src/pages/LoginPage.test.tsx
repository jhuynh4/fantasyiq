import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/session.ts'
import { AuthProvider } from '../auth/AuthContext.tsx'
import { ThemeProvider } from '../theme/ThemeContext.tsx'
import { LoginPage } from './LoginPage.tsx'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderLogin() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/login']}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<p>Signed-in home</p>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('LoginPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    session.clear()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('signs in and moves on to the app', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 }),
    )
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'coach@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Signed-in home')).toBeInTheDocument()
    expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string)).toEqual({
      email: 'coach@example.com',
      password: 'correct-horse',
    })
  })

  it('shows the server message and stays on the page when the credentials are wrong', async () => {
    fetchMock.mockResolvedValueOnce(json({ detail: 'Invalid email or password' }, 401))
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'coach@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.queryByText('Signed-in home')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled())
  })

  it('checks the password length before calling register', async () => {
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }))
    await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('at least 8 characters')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
