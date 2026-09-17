// Use of this source code is governed by a BSD-style license
import { describe, it, expect } from 'vitest'
import { deepMerge, mergeExtensionI18n } from '../src/lang/merge'

describe('deepMerge', () => {
  it('深合并嵌套对象且宿主兄弟键保留', () => {
    const target = { menu: { dashboard: '总览', datasource: '数据源' }, other: 1 }
    deepMerge(target, { menu: { datasource: '扩展数据源', extra: '新增' } })
    expect(target).toEqual({
      menu: { dashboard: '总览', datasource: '扩展数据源', extra: '新增' },
      other: 1
    })
  })

  it('数组整体替换，不逐项合并', () => {
    const target = { routes: [1, 2, 3] }
    deepMerge(target, { routes: [9] })
    expect(target.routes).toEqual([9])
  })

  it('源值为 null/undefined 时按值覆盖', () => {
    const target = { a: 'host', b: 'host' }
    deepMerge(target, { a: null })
    expect(target).toEqual({ a: null, b: 'host' })
  })

  it('目标缺少对应键时直接写入源值', () => {
    const target = {}
    deepMerge(target, { a: { b: 1 } })
    expect(target).toEqual({ a: { b: 1 } })
  })
})

describe('mergeExtensionI18n', () => {
  it('扩展词条并入宿主 locale 且宿主其余键不被覆盖', () => {
    const messages = { 'zh-CN': { menu: { login: '登录' } } }
    mergeExtensionI18n(messages, { 'zh-CN': { ext: { title: '扩展页' }, menu: { extra: '扩展菜单' } } })
    expect(messages['zh-CN']).toEqual({
      menu: { login: '登录', extra: '扩展菜单' },
      ext: { title: '扩展页' }
    })
  })

  it('扩展引入新 locale 时自动创建', () => {
    const messages = { 'zh-CN': {} }
    mergeExtensionI18n(messages, { 'ja-JP': { hello: 'こんにちは' } })
    expect(messages['ja-JP']).toEqual({ hello: 'こんにちは' })
    expect(messages['zh-CN']).toEqual({})
  })

  it('空扩展（stub 场景）为 no-op', () => {
    const messages = { 'zh-CN': { menu: { login: '登录' } }, 'en-US': {} }
    const snapshot = JSON.parse(JSON.stringify(messages))
    mergeExtensionI18n(messages, {})
    mergeExtensionI18n(messages, null)
    mergeExtensionI18n(messages, { 'zh-CN': {}, 'en-US': {} })
    expect(messages).toEqual(snapshot)
  })
})
