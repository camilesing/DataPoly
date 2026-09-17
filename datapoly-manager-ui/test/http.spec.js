// Use of this source code is governed by a BSD-style license
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setHeader, redirectToLoginIfAuthError, setupHttpInterceptors } from '../src/assets/http.js'

// 注册拦截器的伪 axios：捕获 use() 调用以便直接驱动各 handler
function registerInterceptors (router) {
  const notify = vi.fn()
  const axios = {
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() }
    }
  }
  setupHttpInterceptors(axios, router, notify)
  const [requestOk] = axios.interceptors.request.use.mock.calls[0]
  const [responseOk, responseError] = axios.interceptors.response.use.mock.calls[0]
  return { requestOk, responseOk, responseError, notify }
}

function fakeRouter (path) {
  return {
    currentRoute: { path },
    push: vi.fn(() => Promise.resolve())
  }
}

describe('setHeader', () => {
  it('普通对象 headers 直接赋值', () => {
    const config = { headers: {} }
    setHeader(config, 'Accept-Language', 'zh-CN')
    expect(config.headers['Accept-Language']).toBe('zh-CN')
  })

  it('axios 1.x 的 AxiosHeaders 实例走 set()（大小写不敏感）', () => {
    const store = {}
    const config = { headers: { set: vi.fn((k, v) => { store[k.toLowerCase()] = v }) } }
    setHeader(config, 'Accept-Language', 'en-US')
    expect(config.headers.set).toHaveBeenCalledWith('Accept-Language', 'en-US')
    expect(store['accept-language']).toBe('en-US')
  })

  it('headers 缺失时先创建普通对象', () => {
    const config = {}
    setHeader(config, 'Authorization', 'Bearer t')
    expect(config.headers.Authorization).toBe('Bearer t')
  })
})

describe('请求拦截器（token 与语言头）', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  it('有 token 时注入 Bearer Authorization 与 Accept-Language', async () => {
    sessionStorage.setItem('token', 'abc123')
    localStorage.setItem('locale', 'en-US')
    const { requestOk } = registerInterceptors(fakeRouter('/dashboard'))
    const headers = {}
    const config = await requestOk({ url: '/x', headers: { set: (k, v) => { headers[k.toLowerCase()] = v } } })
    expect(headers.authorization).toBe('Bearer abc123')
    expect(headers['accept-language']).toBe('en-US')
    expect(config.url).toBe('/x')
  })

  it('无 token / 无 locale 时使用默认 zh-CN 且不带 Authorization', async () => {
    const { requestOk } = registerInterceptors(fakeRouter('/dashboard'))
    const headers = {}
    await requestOk({ url: '/x', headers: { set: (k, v) => { headers[k.toLowerCase()] = v } } })
    expect(headers.authorization).toBeUndefined()
    expect(headers['accept-language']).toBe('zh-CN')
  })
})

describe('响应拦截器（鉴权跳转与 ResultEntity 归一化）', () => {
  it('2xx 响应体 code=401 时跳转登录页且原样返回响应', () => {
    const router = fakeRouter('/dashboard')
    const { responseOk } = registerInterceptors(router)
    const res = { data: { code: 401, message: 'expired' } }
    expect(responseOk(res)).toBe(res)
    expect(router.push).toHaveBeenCalledWith({ path: '/login' })
  })

  it('已在登录页时不再重复跳转', () => {
    const router = fakeRouter('/login')
    redirectToLoginIfAuthError({ code: 401 }, router, vi.fn())
    expect(router.push).not.toHaveBeenCalled()
  })

  it('业务 404 弹出错误消息而不是登出', () => {
    const notify = vi.fn()
    redirectToLoginIfAuthError({ code: 404, message: '资源不存在' }, fakeRouter('/dashboard'), notify)
    expect(notify).toHaveBeenCalledWith('资源不存在')
    redirectToLoginIfAuthError({ code: 404 }, fakeRouter('/dashboard'), notify)
    expect(notify).toHaveBeenLastCalledWith('Not Found')
  })

  it('非 2xx 带 ResultEntity 体归一化为 resolve({data})', async () => {
    const router = fakeRouter('/dashboard')
    const { responseError } = registerInterceptors(router)
    const body = { code: 7, message: '业务失败' }
    const headers = { 'x-trace': '1' }
    const normalized = await responseError({ response: { data: body, status: 500, headers } })
    expect(normalized).toEqual({ data: body, status: 500, headers })
  })

  it('非 ResultEntity 错误（无 code 字段）原样 reject', async () => {
    const { responseError } = registerInterceptors(fakeRouter('/dashboard'))
    const resp = { data: 'Bad Gateway', status: 502 }
    await expect(responseError({ response: resp })).rejects.toBe(resp)
  })

  it('网络错误（无 response）reject undefined', async () => {
    const { responseError } = registerInterceptors(fakeRouter('/dashboard'))
    await expect(responseError(new Error('network'))).rejects.toBeUndefined()
  })
})
