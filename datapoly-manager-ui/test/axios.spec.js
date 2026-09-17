// Use of this source code is governed by a BSD-style license
import { describe, it, expect, beforeEach, vi } from 'vitest'

// 基础 axios 实例（assets/axios.js）：URL 守卫、API_ROOT 前缀与语言头。
// 通过自定义 adapter 捕获最终请求头，验证 axios 1.x AxiosHeaders 下的真实序列化结果。
import axios from '../src/assets/axios.js'

function withCaptureAdapter (instance) {
  let captured = null
  instance.defaults.adapter = async config => {
    captured = config
    return { data: { code: 0 }, status: 200, statusText: 'OK', headers: {}, config }
  }
  return () => captured
}

beforeEach(() => {
  localStorage.clear()
})

describe('基础请求拦截器', () => {
  it('携带 Accept-Language（localStorage 优先，默认 zh-CN）', async () => {
    const capture = withCaptureAdapter(axios)
    await axios.get('/health')
    expect(capture().headers.get('accept-language')).toBe('zh-CN')

    localStorage.setItem('locale', 'en-US')
    await axios.get('/health')
    expect(capture().headers.get('accept-language')).toBe('en-US')
  })

  it('API_ROOT 存在时为相对 URL 加前缀', async () => {
    vi.resetModules()
    vi.stubEnv('API_ROOT', '/gateway-prefix')
    const fresh = (await import('../src/assets/axios.js')).default
    const capture = withCaptureAdapter(fresh)
    await fresh.get('/user/login')
    expect(capture().url).toBe('/gateway-prefix/user/login')
    vi.unstubAllEnvs()
  })

  it('缺失 URL 时拦截器直接拒绝', async () => {
    const handler = axios.interceptors.request.handlers[0].fulfilled
    await expect(handler({ headers: {} })).rejects.toThrow('Missing URL')
    await expect(handler(undefined)).rejects.toThrow('Config is undefined')
  })
})
