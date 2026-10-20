import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The browser only ever talks to its own origin; in dev, Vite forwards /api to the backend
// (in production, CloudFront does the same), so there is no CORS to configure.
export default defineConfig({
  plugins: [react()],
  server: {
    // File-change events don't cross a Windows-to-container bind mount, so a dev server running in
    // Docker needs to poll for edits.
    watch: process.env.VITE_USE_POLLING ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      '/api': process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
