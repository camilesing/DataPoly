// Use of this source code is governed by a BSD-style license
import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest'
import { loadViewOptions, httpOf } from './helpers/sfcScript'

describe('setting group: the modify-relation dialog', () => {
  const methods = loadViewOptions('../../src/views/setting/group.vue').methods
  const context = () => ({
    moduleAssignments: [],
    initCheckedKeys: [],
    currentGroupId: 0,
    relationReady: false,
    relationFormVisible: false,
    uuid: methods.uuid,
    $t: key => key,
    $message: () => {},
    $http: httpOf(vi.fn(() => new Promise(() => {}))),
    $refs: { relationTree: { getCheckedKeys: () => [] } }
  })

  // jsdom leaves window.alert unimplemented; the failure branch calls it
  let nativeAlert
  beforeAll(() => {
    nativeAlert = window.alert
    window.alert = vi.fn()
  })
  afterAll(() => {
    window.alert = nativeAlert
  })

  it('targets the group that was clicked and cannot save before its tree has arrived', () => {
    // Regression: the group id was only set once the tree had loaded, while the dialog (and its
    // save button) showed at once — saving the still-empty tree posted [] against the previous
    // group and wiped that group's relations
    const ctx = context()
    ctx.currentGroupId = 3 // the group whose dialog was open before

    methods.handleRelation.call(ctx, 0, { id: 5 })

    expect(ctx.currentGroupId).toBe(5)
    expect(ctx.relationFormVisible).toBe(true)

    let posted = 0
    ctx.$http = httpOf(() => {
      posted++
      return Promise.resolve({ data: { code: 0 } })
    })
    methods.handleRelationSave.call(ctx)
    expect(posted).toBe(0)
  })

  it('saves once the tree has loaded', async () => {
    const ctx = context()
    let resolveTree
    const sent = []
    ctx.$http = httpOf(request => {
      if (request && request.method) {
        sent.push(request)
        return Promise.resolve({ data: { code: 0 } })
      }
      return new Promise(resolve => { resolveTree = resolve })
    })

    methods.handleRelation.call(ctx, 0, { id: 5 })
    resolveTree({ data: { code: 0, data: [] } })
    await Promise.resolve()
    await Promise.resolve()

    methods.handleRelationSave.call(ctx)
    expect(sent).toHaveLength(1)
    expect(sent[0].url).toBe('/datapoly/manager/api/v1/assignment/group/5')
  })

  it('stays unsavable when the tree fails to load', async () => {
    const ctx = context()
    let resolveTree
    ctx.$http = httpOf(() => new Promise(resolve => { resolveTree = resolve }))

    methods.handleRelation.call(ctx, 0, { id: 5 })
    resolveTree({ data: { code: 500, message: 'boom' } })
    await Promise.resolve()
    await Promise.resolve()

    let posted = 0
    ctx.$http = httpOf(() => {
      posted++
      return Promise.resolve({ data: { code: 0 } })
    })
    methods.handleRelationSave.call(ctx)
    expect(posted).toBe(0)
  })
})
