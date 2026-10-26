// Use of this source code is governed by a BSD-style license

/**
 * Where the UI keeps the login state: localStorage, not sessionStorage.
 *
 * A manager session now lasts up to a week (datapoly.security.login-token-ttl-seconds,
 * 7 days by default) and survives a restart of the backend, so the browser copy has to
 * survive closing the tab or the browser — sessionStorage would drop a still-valid
 * session on the way out. The keys live in one list so that a logout clears exactly
 * these and nothing else the origin may store (navigation state such as `activePath`
 * stays where it is).
 */
const AUTH_KEYS = ['token', 'username', 'realname', 'role', 'version']

export function getAuth (key) {
  return window.localStorage.getItem(key)
}

export function setAuth (key, value) {
  window.localStorage.setItem(key, value)
}

export function clearAuth () {
  AUTH_KEYS.forEach(key => window.localStorage.removeItem(key))
}