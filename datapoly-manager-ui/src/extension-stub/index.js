// Use of this source code is governed by a BSD-style license
// Compile-time extension fallback: the webpack '@extension' alias resolves here when
// ../../datapoly-extension/front/src is absent, keeping router and i18n assembly no-ops.
export default {
  routes: [],
  i18n: {
    'zh-CN': {},
    'en-US': {}
  }
}