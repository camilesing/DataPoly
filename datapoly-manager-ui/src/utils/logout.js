// Use of this source code is governed by a BSD-style license

import { getAuth, clearAuth } from './authStorage'

/**
 * Log out: revoke the session on the server and drop the local login state.
 *
 * The token travels in the call itself. The request interceptor reads it from
 * localStorage, and axios runs that interceptor only after this function has
 * returned — by then the token is gone, and an anonymous POST /user/logout is
 * turned away with 401 before it reaches the controller, so the session row
 * would live on in DATAPOLY_LOGIN_TOKEN until its TTL expires. Leaving the page
 * is not held up by the request.
 */
export function logout (http, router) {
  const token = getAuth('token')
  if (token) {
    http({
      method: 'POST',
      url: '/user/logout',
      headers: { Authorization: 'Bearer ' + token }
    }).catch(() => {
    })
  }
  clearAuth()
  router.push('/login').catch(() => {
  })
}
