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
    setupFiles: ['./src/test/setup.ts'],
    // The OpenAPI spec lives outside this package and several tests read it directly — the
    // nav/permission and enum-label checks are only worth anything if they read the real one.
    server: { deps: { inline: [] } },
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
