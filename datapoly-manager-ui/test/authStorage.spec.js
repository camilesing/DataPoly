// Use of this source code is governed by a BSD-style license
import { describe, it, expect, beforeEach } from 'vitest'
import { getAuth, setAuth, clearAuth } from '../src/utils/authStorage.js'

describe('authStorage（登录态存 localStorage，跨关标签页/关浏览器保留）', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('setAuth/getAuth 经 localStorage 读写', () => {
    setAuth('token', 'abc123')
    expect(getAuth('token')).toBe('abc123')
    expect(localStorage.getItem('token')).toBe('abc123')
  })

  it('登录态不落在 sessionStorage（一周的会话不能随标签页关闭而丢）', () => {
    setAuth('token', 'abc123')
    expect(sessionStorage.getItem('token')).toBeNull()
  })

  it('clearAuth 清掉全部登录态键', () => {
    setAuth('token', 'abc123')
    setAuth('username', 'admin')
    setAuth('realname', 'admin')
    setAuth('role', 'ADMIN')
    setAuth('version', '1.4.0')

    clearAuth()

    expect(getAuth('token')).toBeNull()
    expect(getAuth('username')).toBeNull()
    expect(getAuth('realname')).toBeNull()
    expect(getAuth('role')).toBeNull()
    expect(getAuth('version')).toBeNull()
  })

  it('clearAuth 不动非登录态键（语言偏好与导航状态各归各的）', () => {
    setAuth('token', 'abc123')
    localStorage.setItem('locale', 'en-US')
    sessionStorage.setItem('activePath', '/dashboard')

    clearAuth()

    expect(localStorage.getItem('locale')).toBe('en-US')
    expect(sessionStorage.getItem('activePath')).toBe('/dashboard')
  })
})