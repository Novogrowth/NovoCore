import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * The backend, reached through Caddy exactly as a browser reaches it in production.
 *
 * Overridable for anyone running the application some other way (a bare `mvn spring-boot:run`
 * on :8080, say), but the default is the running stack, because a development proxy that skips
 * TLS is a development proxy where cookies behave differently from production.
 */
const apiTarget = process.env.VITE_API_TARGET ?? 'https://localhost'

const proxy = {
  target: apiTarget,
  // Caddy routes by Host header, so it has to see its own site address rather than 127.0.0.1.
  changeOrigin: true,
  // Caddy's internal CA issues the development certificate and is not in Node's trust store.
  // This affects the dev proxy only; nothing in the production build speaks TLS.
  secure: false,
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    /*
     * 127.0.0.1, NOT localhost — and this is not a preference.
     *
     * docker/Caddyfile sends `Strict-Transport-Security: max-age=31536000` and
     * NOVOCORE_SITE_ADDRESS is `localhost`, so any browser that has opened the running stack has
     * an HSTS entry for that hostname. HSTS is recorded per host and applies to EVERY port, so
     * http://localhost:5173 is silently upgraded to https and the dev server appears to be
     * broken. 127.0.0.1 is a different host string, carries no HSTS entry, and is still a
     * trustworthy origin — so the backend's `Secure` session cookie is accepted.
     */
    host: '127.0.0.1',
    port: 5173,
    // Fail rather than silently move to 5174: the port is written into documentation.
    strictPort: true,
    /*
     * One origin from the browser's point of view. Without this the session cookie is
     * cross-site, and `SameSite=Strict` (set in application.yml, deliberately) means the browser
     * never sends it — which presents as "the API keeps logging me out".
     *
     * /login and /logout are Spring Security's own endpoints rather than ours, which is why they
     * are listed separately from /api — and why they are absent from the OpenAPI spec.
     */
    proxy: {
      '/api': proxy,
      '/login': proxy,
      '/logout': proxy,
    },
  },
})
