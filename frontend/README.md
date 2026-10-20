# FantasyIQ frontend

React + Vite + TypeScript single-page app for the FantasyIQ API. It is built to static files;
in production the same origin serves the files and forwards `/api/*` to the backend, so the
browser never makes a cross-origin request.

## Running locally

The backend has to be up first (from the repo root):

```bash
docker compose --profile app up -d --build
```

Then, with Node 22 or newer:

```bash
npm install
npm run dev        # http://localhost:5173, /api is proxied to http://localhost:8080
```

Set `API_PROXY_TARGET` to point the dev proxy somewhere else.

The compose stack creates a development admin account (`admin@fantasyiq.dev`, password from
`ADMIN_PASSWORD`, see `docker-compose.yml`). Any account can also be created from the sign-in page.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check, then bundle to `dist/` |
| `npm test` | Unit and component tests (Vitest, jsdom) |
| `npm run lint` | oxlint |

## Layout

- `src/api` — fetch wrapper with token handling (`client.ts`), the in-memory/persisted session
  (`session.ts`), and the auth calls.
- `src/auth`, `src/theme` — React contexts for the signed-in state and light/dark theme.
- `src/styles/tokens.css` — the design tokens (colors, fonts, radii) for both themes.

## Sign-in model

The access token (15 minutes) is kept in memory only. The refresh token is stored in
`localStorage` so a reload keeps you signed in. Refresh tokens are single-use, so concurrent
requests share one refresh call, and a request that gets a 401 refreshes and retries once.
