// Use of this source code is governed by a BSD-style license
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom'
  }
})
