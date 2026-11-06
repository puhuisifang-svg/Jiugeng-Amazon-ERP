import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.smoke.ts',
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:4188', browserName: 'chromium' },
  webServer: {
    command: 'pnpm exec vite preview --host 127.0.0.1 --port 4188 --strictPort',
    url: 'http://127.0.0.1:4188',
    reuseExistingServer: false,
  },
})
