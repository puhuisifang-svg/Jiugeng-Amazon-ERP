import { cpSync, createReadStream, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { createRequire } from 'node:module'
import type { Plugin } from 'vite'

// Ship PDF.js fonts/decoders with the app; previews never fetch from a public CDN.
export function pdfAssets(): Plugin {
  const root = resolve(createRequire(import.meta.url).resolve('pdfjs-dist/package.json'), '..')
  const folders = ['cmaps', 'standard_fonts', 'wasm', 'iccs']
  let outDir = 'dist', build = false
  return {
    name: 'pdf-local-assets',
    configResolved(config) { outDir = config.build.outDir; build = config.command === 'build' },
    configureServer(server) {
      server.middlewares.use('/pdf-assets', (req, res, next) => {
        const path = (req.url || '').split('?')[0]!
        if (!/^\/(cmaps|standard_fonts|wasm|iccs)\/[\w.-]+$/.test(path)) { next(); return }
        const file = resolve(root, `.${path}`)
        if (!existsSync(file)) { next(); return }
        res.setHeader('Content-Type', file.endsWith('.wasm') ? 'application/wasm' : 'application/octet-stream')
        createReadStream(file).pipe(res)
      })
    },
    closeBundle() { if (!build) return; for (const folder of folders) cpSync(resolve(root, folder), resolve(outDir, 'pdf-assets', folder), { recursive: true }) },
  }
}
