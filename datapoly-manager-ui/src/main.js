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
import { mergeExtensionI18n } from './lang/merge'
import { setupHttpInterceptors } from './assets/http.js'
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

Vue.prototype.$http = axios
Vue.config.productionTip = false
if (process.env.NODE_ENV !== 'production') {
  // Debug builds (npm run build:debug) keep Vue devtools functional.
  Vue.config.devtools = true
}
Vue.prototype.$echarts = echarts

// http interceptors (token/language headers, auth-error redirect, ResultEntity
// error normalization) — extracted to assets/http.js and covered by unit tests
setupHttpInterceptors(axios, router, message => ElementUI.Message.error(message))

/* eslint-disable no-new */
new Vue({
  el: '#app',
  router,
  i18n,
  components: {App},
  template: '<App/>'
})
