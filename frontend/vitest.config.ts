import path from 'path'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    /*
     * The document's origin, which is what a relative request resolves against.
     *
     * jsdom defaults to `http://localhost:3000`, so `/api/me` — the path the generated client
     * actually uses — resolved to a URL no mock handler matched, and the request failed as
     * "unhandled" instead of being answered. Tests then passed or failed for reasons that had
     * nothing to do with the code under test. Pinning it to `http://localhost` makes a relative
     * path resolve where the handlers are, which is also where the real backend is.
     */
    environmentOptions: { jsdom: { url: 'http://localhost' } },
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
