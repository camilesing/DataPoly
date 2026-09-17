// Use of this source code is governed by a BSD-style license

// 从 main.js 抽出的扩展 i18n 深合并逻辑（行为不变，供单测覆盖）：
// 在创建 i18n 实例前把 UI 扩展词条深合并进宿主 messages，扩展页共享同一个
// $t() 与 locale 处理，且宿主键（含共享的 `menu` 命名空间）保持不被覆盖。
// '@extension' 入口指向仓库内 stub 时为 no-op。
export function mergeExtensionI18n (target, ext) {
  Object.keys(ext || {}).forEach(locale => {
    deepMerge(target[locale] || (target[locale] = {}), ext[locale] || {})
  })
}

export function deepMerge (target, source) {
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
