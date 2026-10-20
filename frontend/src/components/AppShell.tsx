import { useEffect, useRef, useState } from 'react'
import { Columns2, ListOrdered } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.tsx'
import { Logo } from './Logo.tsx'
import { ThemeToggle } from './ThemeToggle.tsx'

function initials(email: string | null): string {
  if (!email) return '?'
  const parts = email.split('@')[0].split(/[._-]+/).filter(Boolean)
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : (parts[0] ?? '?').slice(0, 2)
  return letters.toUpperCase()
}

function AccountMenu() {
  const { email, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  return (
    <div className="account" ref={ref}>
      <button
        type="button"
        className="avatar"
        aria-label="Account menu"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((value) => !value)}
      >
        {initials(email)}
      </button>
      {open && (
        <div className="account-menu" role="menu">
          <p className="account-email">{email}</p>
          <button type="button" role="menuitem" className="menu-item" onClick={logout}>
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}

export function AppShell() {
  // A player page belongs to the rankings, so that link stays lit while one is open.
  const onPlayerPage = useLocation().pathname.startsWith('/players/')
  return (
    <>
      <header className="app-header">
        <Link to="/" className="logo-link" aria-label="FantasyIQ home">
          <Logo />
        </Link>
        <nav className="app-nav" aria-label="Main">
          <NavLink to="/" end className={({ isActive }) => (isActive || onPlayerPage ? 'active' : '')}>
            <ListOrdered size={18} aria-hidden="true" />
            Rankings
          </NavLink>
          <NavLink to="/compare">
            <Columns2 size={18} aria-hidden="true" />
            Compare
          </NavLink>
        </nav>
        <div className="header-actions">
          <ThemeToggle />
          <AccountMenu />
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </>
  )
}
