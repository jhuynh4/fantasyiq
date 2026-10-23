import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { ApiError } from '../api/client.ts'
import { useAuth } from '../auth/AuthContext.tsx'
import { Logo } from '../components/Logo.tsx'
import { ThemeToggle } from '../components/ThemeToggle.tsx'

type Mode = 'login' | 'register'

const MIN_PASSWORD_LENGTH = 8

const SAMPLE_FACTORS = [
  { name: 'Season average', value: 15.9 },
  { name: 'Matchup', value: 0.8 },
  { name: 'Vegas implied total', value: 0.52 },
  { name: 'Weather', value: -1.2 },
]

function signed(value: number): string {
  return `${value < 0 ? '−' : '+'}${Math.abs(value).toFixed(2)}`
}

function ScorePreview() {
  const total = SAMPLE_FACTORS.reduce((sum, factor) => sum + factor.value, 0)
  return (
    <figure className="score-preview">
      <figcaption className="eyebrow-small">Score anatomy preview</figcaption>
      <p className="score-total mono">{total.toFixed(2)}</p>
      <ul>
        {SAMPLE_FACTORS.map((factor) => (
          <li key={factor.name}>
            <span>{factor.name}</span>
            <span className={`mono ${factor.value < 0 ? 'negative' : 'positive'}`}>{signed(factor.value)}</span>
          </li>
        ))}
      </ul>
      <p className="muted small">An illustrative example. Every real score is the plain sum of its factors.</p>
    </figure>
  )
}

export function LoginPage() {
  const auth = useAuth()
  const location = useLocation()
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (auth.status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/'} replace />
  }

  const registering = mode === 'register'

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (registering && password.length < MIN_PASSWORD_LENGTH) {
      setError(`Choose a password of at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }
    setSubmitting(true)
    try {
      if (registering) await auth.register(email.trim(), password, displayName)
      else await auth.login(email.trim(), password)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
      setSubmitting(false)
    }
  }

  function switchMode() {
    setMode(registering ? 'login' : 'register')
    setError(null)
  }

  return (
    <div className="login-page">
      <header className="login-header">
        <Logo />
        <ThemeToggle />
      </header>

      <main className="login-main">
        <section className="login-context" aria-label="About FantasyIQ">
          <p className="eyebrow">The reason behind the rank</p>
          <h1>Less guesswork. More understanding.</h1>
          <p className="muted lead">
            FantasyIQ ranks players for the week and shows exactly which factors moved each score, so you can judge the
            reasoning instead of trusting a black box.
          </p>
          <ScorePreview />
        </section>

        <section className="login-panel" aria-labelledby="auth-heading">
          <h2 id="auth-heading">{registering ? 'Create your account' : 'Welcome back'}</h2>
          <p className="muted">
            {registering ? 'Create an account to explore the numbers behind the rankings.' : 'Sign in to explore the numbers behind the rankings.'}
          </p>

          <form onSubmit={onSubmit} noValidate>
            {registering && (
              <label className="field">
                <span>Display name (optional)</span>
                <input
                  type="text"
                  autoComplete="nickname"
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                />
              </label>
            )}
            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                autoComplete={registering ? 'new-password' : 'current-password'}
                required
                aria-describedby={registering ? 'password-hint' : undefined}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              {registering && (
                <small id="password-hint" className="muted">
                  At least {MIN_PASSWORD_LENGTH} characters.
                </small>
              )}
            </div>

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <button type="submit" className="primary-button" disabled={submitting || !email || !password}>
              {submitting ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}
            </button>
          </form>

          <p className="switch-mode muted">
            {registering ? 'Already have an account?' : 'New to FantasyIQ?'}{' '}
            <button type="button" className="link-button" onClick={switchMode}>
              {registering ? 'Sign in' : 'Create an account'}
            </button>
          </p>
          <p className="muted small">Your password is stored hashed, never in plain text.</p>
        </section>
      </main>
    </div>
  )
}
