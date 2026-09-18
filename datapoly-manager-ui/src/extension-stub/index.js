// Use of this source code is governed by a BSD-style license
// Compile-time extension fallback: the webpack '@extension' alias resolves here when
// ../../datapoly-extension/front/src is absent, keeping router, i18n and login-page
// assembly no-ops.
export default {
  routes: [],
  // Components rendered below the password login button by src/views/login/index.vue.
  loginExtras: [],
  i18n: {
    'zh-CN': {},
    'en-US': {}
  }
}