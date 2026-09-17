// Use of this source code is governed by a BSD-style license

// 从 main.js 抽出的 HTTP 拦截器与鉴权跳转逻辑（行为不变，供单测覆盖）。
// axios 1.x 起拦截器里的 config.headers 是 AxiosHeaders 实例，统一经 setHeader
// 写入以同时兼容实例与普通对象两种形态。

export function setHeader (config, name, value) {
  if (config.headers && typeof config.headers.set === 'function') {
    config.headers.set(name, value)
  } else {
    config.headers = config.headers || {}
    config.headers[name] = value
  }
}

export function redirectToLoginIfAuthError (body, router, notifyError) {
  if (!body) {
    return
  }
  if (body.code === 401 || body.code === 403) {
    // Only redirect when not already on the login page
    if (router.currentRoute.path !== '/login') {
      router.push({path: '/login'}).catch(() => {
      })
    }
    return
  }
  if (body.code === 404 && router.currentRoute.path !== '/login') {
    // Business 404 is not an auth failure — surfacing it instead of silently logging out
    notifyError(body.message || 'Not Found')
  }
}

export function setupHttpInterceptors (axios, router, notifyError) {
  // http request interceptor
  axios.interceptors.request.use(config => {
    // Attach Authorization header with token if present
    const token = sessionStorage.getItem('token')
    if (token) {
      setHeader(config, 'Authorization', 'Bearer ' + token)
    }

    // Send language header for backend i18n
    const locale = localStorage.getItem('locale') || 'zh-CN'
    setHeader(config, 'Accept-Language', locale)

    return config
  }, function (error) {
    return Promise.reject(error)
  })

  // Response interceptor
  axios.interceptors.response.use(res => {
    redirectToLoginIfAuthError(res.data, router, notifyError)

    return res
  }, error => {
    // Backend now maps real HTTP status from error codes (H5): for non-2xx
    // responses with a ResultEntity body, normalize to the old shape (resolve
    // {data}) so page-level code checking res.data.code stays unchanged
    const resp = error.response
    if (resp && resp.data && resp.data.code !== undefined) {
      redirectToLoginIfAuthError(resp.data, router, notifyError)
      return Promise.resolve({data: resp.data, status: resp.status, headers: resp.headers})
    }
    return Promise.reject(resp)
  })
}
