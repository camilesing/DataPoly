// Use of this source code is governed by a BSD-style license
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parseComponent } from 'vue-template-compiler'

const nodeRequire = createRequire(import.meta.url)

/**
 * A page's script as a plain options object — no template, no mounting: tests drive single
 * methods against a context of their own. Default imports the page pulls in for rendering are
 * stubbed, since nothing here renders.
 */
export function loadViewOptions (relativePath) {
  const sfc = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
  const script = parseComponent(sfc).script.content
    .replace(/^\s*import\s+Vue\s+from\s+['"][^'"]+['"];?\s*$/gm, "const Vue = require('vue');")
    .replace(/^\s*import\s+(\w+)\s+from\s+['"][^'"]+['"];?\s*$/gm, 'const $1 = {};')
    .replace(/export default/, 'return')
  return new Function('require', script)(nodeRequire)
}

/** The page calls the axios instance itself for POSTs and its .get for reads. */
export function httpOf (handler) {
  const http = handler
  http.get = handler
  return http
}
