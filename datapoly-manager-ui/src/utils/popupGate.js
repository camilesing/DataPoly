// Use of this source code is governed by a BSD-style license

/**
 * One popup per failure, not one per request.
 *
 * A burst of parallel calls that all fail the same way — the classic case being a
 * stale login session, where every request on the page answers 401 at once — pops
 * one popup per request, one after another: each page-level error branch calls
 * `alert(prefix + res.data.message)` on its own, and the extension UI (compiled into
 * this same bundle) does the same. The login redirect in assets/http.js is already
 * de-duplicated; this gate does the same for the popups, at the only choke point the
 * page-level call sites across host and extension pages share.
 *
 * The window is measured from the moment the previous popup was *closed*, not from
 * when it was opened: a native alert blocks the JS thread, so the queued duplicates
 * only run their callbacks after the user dismisses the first one — a window counted
 * from "shown" would let the whole queue through the moment the user clicks 确定.
 *
 * Two keys feed the same rule:
 *  - a popup whose text contains a message `noteAuthMessage` was told to treat as a
 *    login-state error shares one key across pages, so `加载失败：…` here and
 *    `加载列表失败:…` there count as the same event and only one of them shows;
 *  - otherwise the popup's own text is the key, which merges identical repeats of any
 *    other message within the window.
 */
const DEDUPE_WINDOW_MS = 3000

const openKeys = new Set()
const closedAt = new Map()
let authMessages = []

/**
 * Register the backend message of a login-state error (the response interceptor calls
 * this for the codes that also trigger the redirect to /login). Popups containing it
 * are treated as one event for the window, whatever page prefix they carry. Entries
 * older than the window are dropped on the way in — nothing lingers past a burst.
 */
export function noteAuthMessage (message) {
  if (!message || typeof message !== 'string') {
    return
  }
  const now = Date.now()
  authMessages = authMessages.filter(entry => now - entry.at < DEDUPE_WINDOW_MS)
  if (!authMessages.some(entry => entry.text === message)) {
    authMessages.push({text: message, at: now})
  }
}

function keyFor (text) {
  const now = Date.now()
  for (let i = 0; i < authMessages.length; i++) {
    const entry = authMessages[i]
    if (now - entry.at < DEDUPE_WINDOW_MS && text.indexOf(entry.text) !== -1) {
      return 'auth\u0000' + entry.text
    }
  }
  return 'text\u0000' + text
}

function isSuppressed (key) {
  if (openKeys.has(key)) {
    return true
  }
  const closed = closedAt.get(key)
  return closed !== undefined && Date.now() - closed < DEDUPE_WINDOW_MS
}

function markShown (key) {
  openKeys.add(key)
}

function markClosed (key) {
  openKeys.delete(key)
  closedAt.set(key, Date.now())
}

// Native alert: the call itself returns only after the user dismisses it, so the
// close time is simply when it returns.
function wrapNativeAlert (alertFn) {
  return function (text) {
    const key = keyFor(String(text))
    if (isSuppressed(key)) {
      return
    }
    markShown(key)
    try {
      return alertFn.call(window, text)
    } finally {
      markClosed(key)
    }
  }
}

// element-ui MessageBox (Vue.prototype.$alert): not blocking, so the close time comes
// from the promise the box returns. No call site chains on that promise (verified), so
// resolving it when suppressed is inert; it keeps any `await`-style caller moving.
function wrapMessageBoxAlert (alertFn) {
  return function () {
    const args = arguments
    const key = keyFor(args[0] == null ? '' : String(args[0]))
    if (isSuppressed(key)) {
      return Promise.resolve()
    }
    markShown(key)
    let result
    try {
      result = alertFn.apply(this, args)
    } catch (err) {
      markClosed(key)
      throw err
    }
    if (result && typeof result.then === 'function') {
      const close = () => markClosed(key)
      result.then(close, close)
    } else {
      markClosed(key)
    }
    return result
  }
}

// element-ui Message (Vue.prototype.$message) — error toasts pile up the same way.
// It returns immediately, so the window starts at show time (its own auto-close adds
// to the visibility, not to the window).
function wrapToast (messageFn) {
  return function (options) {
    const text = typeof options === 'string'
      ? options
      : (options && typeof options === 'object' && options.message != null ? String(options.message) : '')
    const key = keyFor(text)
    if (isSuppressed(key)) {
      return
    }
    markShown(key)
    try {
      return messageFn.apply(this, arguments)
    } finally {
      markClosed(key)
    }
  }
}

/**
 * Wrap the popup entry points once, at startup. $confirm/$prompt are deliberately not
 * wrapped: they ask a question, never repeat an error, and must always render.
 */
export function installPopupGate (Vue) {
  window.alert = wrapNativeAlert(window.alert)
  if (Vue && Vue.prototype) {
    if (typeof Vue.prototype.$alert === 'function') {
      Vue.prototype.$alert = wrapMessageBoxAlert(Vue.prototype.$alert)
    }
    if (typeof Vue.prototype.$message === 'function') {
      Vue.prototype.$message = wrapToast(Vue.prototype.$message)
    }
  }
}