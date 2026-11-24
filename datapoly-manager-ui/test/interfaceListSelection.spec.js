// Use of this source code is governed by a BSD-style license
import { describe, it, expect } from 'vitest'
import { loadViewOptions } from './helpers/sfcScript'

describe('interface list: rows selected for the batch export', () => {
  const handleSelectionChange = loadViewOptions('../../src/views/interface/list.vue').methods.handleSelectionChange

  it('follows the current selection: a row that is unchecked leaves the batch', () => {
    // Regression: ids were only ever added, so an unchecked row stayed in the export until the
    // whole selection was cleared
    const ctx = { idsSelected: [], isSelected: true }
    handleSelectionChange.call(ctx, [{ id: 1 }, { id: 2 }])
    expect(ctx.idsSelected).toEqual([1, 2])
    expect(ctx.isSelected).toBe(false)

    handleSelectionChange.call(ctx, [{ id: 2 }])
    expect(ctx.idsSelected).toEqual([2])

    handleSelectionChange.call(ctx, [])
    expect(ctx.idsSelected).toEqual([])
    expect(ctx.isSelected).toBe(true)
  })
})
