// Use of this source code is governed by a BSD-style license
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { can, isAdmin, refreshPermissions, matrixSnapshot,
  __resetForTest, __installForTest } from '../src/utils/permissions.js'
import { getAuth, setAuth, clearAuth } from '../src/utils/authStorage.js'

describe('permissions（前端权限判据唯一一份）', () => {
  beforeEach(() => {
    localStorage.clear()
    __resetForTest()
  })

  it('矩阵未加载时按默认只读：只放行 R', () => {
    setAuth('role', 'USER')
    expect(can('DATASOURCE', 'R')).toBe(true)
    expect(can('DATASOURCE', 'C')).toBe(false)
    expect(can('DATASOURCE', 'U')).toBe(false)
    expect(can('DATASOURCE', 'X')).toBe(false)
  })

  it('ADMIN（登录响应 role）全通过，不依赖矩阵加载', () => {
    setAuth('role', 'ADMIN')
    expect(isAdmin()).toBe(true)
    expect(can('DATASOURCE', 'D')).toBe(true)
    expect(can('FIREWALL', 'U')).toBe(true)
  })

  it('加载后按有效矩阵判定：已授予放行、未授予拒绝、未配置模块回到只读', () => {
    setAuth('role', 'USER')
    __installForTest({ DATASOURCE: ['R', 'C', 'U'] })
    expect(can('DATASOURCE', 'C')).toBe(true)
    expect(can('DATASOURCE', 'D')).toBe(false)
    // 未配置的模块：有效矩阵由后端展开（默认只读 R）
    expect(can('FIREWALL', 'R')).toBe(false)
    expect(matrixSnapshot().ready).toBe(true)
  })

  it('refreshPermissions 解析 /user/permission/self 响应并置 ready', async () => {
    setAuth('role', 'USER')
    const http = {
      get: vi.fn().mockResolvedValue({
        data: {
          code: 0,
          data: [
            { module: 'DATASOURCE', actions: ['R', 'C'] },
            { module: 'API', actions: ['R'] }
          ]
        }
      })
    }
    const snapshot = await refreshPermissions(http)
    expect(http.get).toHaveBeenCalledWith('/datapoly/manager/api/v1/user/permission/self')
    expect(snapshot.ready).toBe(true)
    expect(can('DATASOURCE', 'C')).toBe(true)
    expect(can('API', 'C')).toBe(false)
    expect(can('API', 'R')).toBe(true)
  })

  it('refreshPermissions 失败时保持保守（未加载=只读）', async () => {
    setAuth('role', 'USER')
    __installForTest({}, false)
    const http = { get: vi.fn().mockRejectedValue(new Error('boom')) }
    const snapshot = await refreshPermissions(http)
    expect(snapshot.ready).toBe(false)
    expect(can('DATASOURCE', 'C')).toBe(false)
  })

  it('无登录态时 role 为空 → 非 ADMIN', () => {
    clearAuth()
    expect(getAuth('role')).toBeFalsy()
    expect(isAdmin()).toBe(false)
  })
})
