import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { pdfAssets } from './pdfAssets.ts'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  build: {
    outDir: process.env.ERP_BUILD_OUT_DIR || 'dist',
  },
  plugins: [vue(), pdfAssets()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    restoreMocks: true,
  },
})
