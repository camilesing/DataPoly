// Use of this source code is governed by a BSD-style license
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { logout } from '../src/utils/logout.js'
import { setupHttpInterceptors } from '../src/assets/http.js'

// 与 axios 一致：请求拦截器在调用之后的微任务里才执行，此时 logout 已经返回
function axiosLikeHttp (requestInterceptor, respond) {
  const sent = []
  const http = vi.fn(config => Promise.resolve(config)
    .then(requestInterceptor)
    .then(withHeaders => {
      sent.push(withHeaders)
      return respond ? respond() : { data: { code: 200 } }
    }))
  return { http, sent }
}

// 把真实拦截器注册到伪 axios 上，只取出请求拦截器来驱动
function realRequestInterceptor () {
  const request = { use: vi.fn() }
  setupHttpInterceptors(
    { interceptors: { request, response: { use: vi.fn() } } },
    { currentRoute: { path: '/dashboard' }, push: vi.fn(() => Promise.resolve()) },
    vi.fn()
  )
  return request.use.mock.calls[0][0]
}

function flush () {
  return new Promise(resolve => setTimeout(resolve, 0))
}

describe('logout', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('登出请求带 Bearer token：本地 token 已清，拦截器之后执行也读不到它', async () => {
    localStorage.setItem('token', 'abc123')
    const { http, sent } = axiosLikeHttp(realRequestInterceptor())
    const router = { push: vi.fn(() => Promise.resolve()) }

    logout(http, router)

    // 清本地状态与跳转都不等待请求
    expect(localStorage.getItem('token')).toBeNull()
    expect(router.push).toHaveBeenCalledWith('/login')

    await flush()
    expect(sent).toHaveLength(1)
    expect(sent[0].url).toBe('/user/logout')
    expect(sent[0].method).toBe('POST')
    expect(sent[0].headers.Authorization).toBe('Bearer abc123')
  })

  it('请求失败不影响登出，也不留下未处理的 rejection', async () => {
    localStorage.setItem('token', 'abc123')
    const { http, sent } = axiosLikeHttp(realRequestInterceptor(), () => Promise.reject(new Error('network')))
    const router = { push: vi.fn(() => Promise.resolve()) }

    logout(http, router)

    expect(localStorage.getItem('token')).toBeNull()
    expect(router.push).toHaveBeenCalledWith('/login')
    await flush()
    expect(sent).toHaveLength(1)
  })

  it('没有 token 时不发请求，仍然清登录态并跳登录页', () => {
    localStorage.setItem('username', 'someone')
    const { http } = axiosLikeHttp(realRequestInterceptor())
    const router = { push: vi.fn(() => Promise.resolve()) }

    logout(http, router)

    expect(http).not.toHaveBeenCalled()
    expect(localStorage.getItem('username')).toBeNull()
    expect(router.push).toHaveBeenCalledWith('/login')
  })

  it('只清登录态的键，不动 locale', async () => {
    localStorage.setItem('token', 'abc123')
    localStorage.setItem('locale', 'zh-CN')
    const { http } = axiosLikeHttp(realRequestInterceptor())

    logout(http, { push: vi.fn(() => Promise.resolve()) })

    expect(localStorage.getItem('locale')).toBe('zh-CN')
    expect(localStorage.getItem('token')).toBeNull()
    await flush()
  })
})
