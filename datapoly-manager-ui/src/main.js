// The Vue build version to load with the `import` command
// (runtime-only or standalone) has been set in webpack.base.conf with an alias.
import Vue from 'vue'
import App from './App'
import router from './router'
import axios from './assets/axios.js';
import ElementUI from 'element-ui';
import VueI18n from 'vue-i18n'
import messages from './lang'
import extension from '@extension'
import './assets/iconfont/iconfont.css'
import './assets/dbicon/iconfont.css'
import './assets/dbicon/iconfont.js'
import './assets/sysicon/iconfont.css'
import 'element-ui/lib/theme-chalk/index.css';
import * as echarts from 'echarts'
import VueCodeMirror from 'vue-codemirror'
import 'codemirror/lib/codemirror.css'
import JsonViewer from 'vue-json-viewer'

Vue.use(VueCodeMirror)
Vue.use(ElementUI)
Vue.use(JsonViewer)

// Initialize i18n: use browser language or default to Chinese
const browserLang = navigator.language || 'zh-CN'
const defaultLocale = browserLang.startsWith('en') ? 'en-US' : 'zh-CN'

// Read user preference from localStorage
const savedLocale = localStorage.getItem('locale') || defaultLocale

Vue.use(VueI18n)

// Deep-merge UI extension dictionaries into the base messages before the i18n instance
// is created, so extension pages share the same $t() and locale handling while host keys
// (including the shared `menu` namespace) are preserved. No-op when the '@extension'
// entry is the in-repo stub.
mergeExtensionI18n(messages, extension.i18n)

const i18n = new VueI18n({
  locale: savedLocale,
  messages
})

function mergeExtensionI18n (target, ext) {
  Object.keys(ext || {}).forEach(locale => {
    deepMerge(target[locale] || (target[locale] = {}), ext[locale] || {})
  })
}

function deepMerge (target, source) {
  Object.keys(source).forEach(key => {
    const value = source[key]
    if (value && typeof value === 'object' && !Array.isArray(value)
      && target[key] && typeof target[key] === 'object') {
      deepMerge(target[key], value)
    } else {
      target[key] = value
    }
  })
}

Vue.prototype.$http = axios
Vue.config.productionTip = false
Vue.prototype.$echarts = echarts


// http request interceptor
axios.interceptors.request.use(config => {

  // Attach Authorization header with token if present
  let token = sessionStorage.getItem('token');
  if (token) {
    config.headers.Authorization = 'Bearer ' + token;
  }

  // Send language header for backend i18n
  const locale = localStorage.getItem('locale') || 'zh-CN';
  config.headers['Accept-Language'] = locale;

  return config;
}, function (error) {
  return Promise.reject(error)
})

// Response interceptor
axios.interceptors.response.use(res => {
  redirectToLoginIfAuthError(res.data)

  return res
}, error => {
  // Backend now maps real HTTP status from error codes (H5): for non-2xx
  // responses with a ResultEntity body, normalize to the old shape (resolve
  // {data}) so page-level code checking res.data.code stays unchanged
  const resp = error.response
  if (resp && resp.data && resp.data.code !== undefined) {
    redirectToLoginIfAuthError(resp.data)
    return Promise.resolve({data: resp.data, status: resp.status, headers: resp.headers})
  }
  return Promise.reject(resp)
})

function redirectToLoginIfAuthError(body) {
  if (body && (body.code === 401 || body.code === 403 || body.code === 404)) {
    // Only redirect when not already on the login page
    if (router.currentRoute.path !== '/login') {
      router.push({path: "/login"}).catch(() => {
      });
    }
  }
}

/* eslint-disable no-new */
new Vue({
  el: '#app',
  router,
  i18n,
  components: {App},
  template: '<App/>'
})
