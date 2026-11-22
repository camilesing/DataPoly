# Login Extensions (Third-party Sign-in) — An Integration Guide

Language: [简体中文](../zh/login-extension.md) [English](login-extension.md)

The built-in management UI offers exactly one way to sign in: username and password (`POST /user/login`). Deciding
*who the caller is* can be left to someone else. Any **account provider** that can authenticate a user on the
outside — an enterprise IM, corporate SSO, an OAuth / OIDC application — may add its entry point to the login page.
Once authentication succeeds, the provider opens a session in the host that is **indistinguishable from a password
session**: same table, same lifetime, same interceptor. Everything that follows — authorization, logout, eviction on
a password change — is the host's existing logic.

The host contributes exactly two things: a **mount point** on the login page (section 2) and a **session-issuing
entry point** (section 3). How the provider talks to the external identity source, and how an external identity maps
to a local account, is entirely up to the provider. The reference implementation is the Feishu login, which lives in
the separate extension repository `datapoly-extension` (module `datapoly-extension-feishu`) and is not part of this
repository; this document describes the host side of the contract.

> Cloning, building and assembling the extension repository is covered under "Host extension assembly" in
> [build-deploy.md](build-deploy.md): the backend modules build into jars dropped into `lib-extra/`, which puts them
> on manager's classpath in the release; the front-end sources are compiled into the management UI through the
> webpack `@extension` alias.

## 1. End-to-end sequence

Taking a typical authorization-code flow (the steps that talk to the identity source are the provider's to design):

```text
Browser                      Identity source          Provider (extension)          Host manager
 │ Login page: the loginExtras components render below the password button
 │ Click the entry ─────────▶ Start auth ───────────▶ │
 │ ◀──────────── Authenticated (authorization code / assertion / …)
 │ Hand the result to a provider endpoint ──────────▶ ① Find the account row by external identity, provision if absent
 │                                                    │   (DATAPOLY_SYSTEM_USER)
 │                                                  ② LoginSessionService.issue(user)
 │                                                    │   Write DATAPOLY_LOGIN_TOKEN, get accessToken
 │ ◀────────────── accessToken and account info ─────┘
 │ Write localStorage (token / username / realname / role), navigate to /dashboard
 │
 │ Every later request carries Authorization: Bearer <accessToken> ──▶ ③ Session interceptor: resolve + re-read account row
```

① and ② are implemented by the provider; ③ is existing host logic, and password sessions go through the same ③.

## 2. Front end: the `loginExtras` mount point

**Entry**. When the management UI is built, webpack points the `@extension` alias at `datapoly-extension/front/src`
(this applies only when its entry file `index.js` exists); when that directory is absent the alias falls back to the
in-repo empty implementation, `datapoly-manager-ui/src/extension-stub/index.js`. The entry's default export has three
members: `routes` (extension pages), `i18n` (messages, deep-merged into the host's language packs) and `loginExtras`
(login-page components). A login extension only needs `loginExtras`:

```js
// datapoly-extension/front/src/index.js
import FeishuLogin from './feishu/FeishuLogin.vue'

export default {
  routes: [],
  loginExtras: [FeishuLogin],       // Login page: rendered below the password button
  i18n: {
    'zh-CN': { feishu: { login: '使用飞书登录' } },
    'en-US': { feishu: { login: 'Sign in with Feishu' } }
  }
}
```

**Mount point**. `datapoly-manager-ui/src/views/login/index.vue` renders the components in `loginExtras` one by one,
in array order, below the password button (`<component :is>`), inside the `.extension-login` container — centered and
separated from the form above by a divider. When the array is empty the whole block is left out, so the login page of
a plain open-source build without extensions is unchanged. The array is fixed at build time (the login page reads it
once) and never grows or shrinks at run time — a provider that is not enabled decides, inside its own component, not
to render.

**Authoring rules**. The extension sources are compiled into the same bundle as the host and share its single
Vue / Element UI instance and its `this.$http` / `this.$router` / `this.$t`, and they can import host modules directly
(the `@` alias points at `datapoly-manager-ui/src`). So do not install `vue` or `element-ui` into the extension's own
`node_modules` — that would bundle a second copy. Give message keys their own namespace (as in `feishu.*` above)
rather than reusing host keys.

**Finishing the sign-in**. The host's `handleLogin` handles the password flow only; once an extension component has
the login result from the provider's back end, it finishes the sign-in itself — writing the same keys, then
navigating, exactly as the password flow does:

```js
import { setAuth } from '@/utils/authStorage'

// res: the response this.$http resolved with; data has the shape of /user/login (see 3.2)
const data = res.data.data
setAuth('token', data.accessToken)      // the request interceptor turns this into Authorization: Bearer
setAuth('username', data.appKey)        // the account name in AccessToken is the appKey field
setAuth('realname', data.realName)
setAuth('role', data.role || '')
this.$router.push({ path: '/dashboard' })
```

The login state lives in **localStorage**; the keys are centralized in `datapoly-manager-ui/src/utils/authStorage.js`:

- `token` is the only key that affects authorization — the request interceptor in `src/assets/http.js` reads it to
  attach the Bearer header. There is no router guard: being "signed in" means having a usable `token` in
  localStorage. Without it, the first API call on any business page answers 401 and the response interceptor takes
  the page back to `/login`.
- `username` / `realname` are shown in the user menu at the top right; `username` is also what the personal-info page
  loads by.
- `role` is currently written only; the host UI does not yet trim what it shows by role.
- `version` is optional: the password flow requests `/datapoly/manager/api/v1/health/version` and stores `data`
  there so the user menu can show the version.

## 3. Back end: the account provider

The provider is a Spring component inside the extension jar, running in the **manager process**. The extension jar
sits on the classpath shared by all three services, but login-related endpoints belong on manager only (the gateway
forwards fixed prefixes to it, see [3.3](#33-session-interceptor-and-reachable-paths)). Its beans are picked up
automatically only inside manager's component-scan roots (`ManagerApplication.scanBasePackages`, which includes
`com.cs.manager`, `com.cs.core.extension` and others); anything outside them is registered by the extension module's
own configuration. The provider has exactly two jobs: map the external identity to a local account row, then call
`LoginSessionService.issue` to open the session.

### 3.1 The account row

An account is a row of `DATAPOLY_SYSTEM_USER`; `username` carries a unique index under both MySQL and PostgreSQL.
Lookup and provisioning:

```java
@Resource
private SystemUserDao systemUserDao;               // com.cs.persistence.dao
@Resource
private LoginSessionService loginSessionService;   // com.cs.core.service

SystemUserEntity user = systemUserDao.findByUsername(username);
if (user == null) {
    // Provisioned on first sign-in. password / salt are NOT NULL but only ever read by the
    // password flow: store the bcrypt value of a random password, so a stray password attempt
    // lands on the ordinary "invalid username or password" branch
    String salt = BCrypt.gensalt();                // cn.hutool.crypto.digest.BCrypt
    user = SystemUserEntity.builder()
            .username(username)
            .realName(realName)
            .password(PasswordUtils.encryptPassword(UUID.randomUUID().toString(), salt))  // com.cs.common.util
            .salt(salt)
            .locked(false)
            .build();                              // role left null takes the column default, USER
    systemUserDao.insert(user);
}
```

Points to note:

- **The provider decides how `username` is derived, but it must be stable**: the same external identity has to land
  on the same row every time. Decide up front which identity-source identifier to key on (open_id, union_id, the
  employee number, …); otherwise one person ends up with several accounts, each with its own role and sessions.
- **Role**: the `user_role` column defaults to `USER`. By the column's definition `ADMIN` is the administrator (full
  CRUD) and `USER` the ordinary user (read / invoke only), but a role only decides what the UI shows — authorization
  always goes by the token. Keep the default when provisioning and grant `ADMIN` explicitly where it is really needed.
- **Locking**: an account whose `locked` is true is refused on the very next request, even for sessions already open
  (see 3.3). The host has no endpoint for locking or unlocking; change the row's `locked` column in the metadata
  database directly when needed.

### 3.2 Issuing the session

`LoginSessionService.issue(user)` is the **only** place a session is issued — the password flow uses it too: it
inserts a row into `DATAPOLY_LOGIN_TOKEN` and returns the `accessToken`. Lifetime = issue time +
`datapoly.security.login-token-ttl-seconds` (604800 = 7 days by default), **absolute** — activity never extends it.
The expiry is written down at issue time, so lowering the setting only affects sessions issued afterwards (the change
itself takes effect after a manager restart). Sessions are persisted, so a manager restart no longer logs anyone out.

For the login result, reuse the password flow's DTO and response wrapper so the front end can share one piece of
finish-up code:

```java
String token = loginSessionService.issue(user);
return ResultEntity.success(AccessToken.builder()
        .realName(user.getRealName())
        .appKey(user.getUsername())          // the account name in AccessToken is the appKey field
        .accessToken(token)
        .createTimestamp(System.currentTimeMillis() / 1000)
        .expireSeconds(loginSessionService.getTtlSeconds())
        .role(user.getRole())
        .build());
```

Response body (`createTimestamp` is not serialized):

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "realName": "Zhang San",
    "appKey": "feishu_zhangsan",
    "accessToken": "…",
    "expireSeconds": 604800,
    "role": "USER"
  }
}
```

### 3.3 Session interceptor and reachable paths

Manager's session interceptor (`datapoly-manager/src/main/java/com/cs/manager/config/AuthenticationConfiguration.java`)
covers every handler method outside the exempt list, and makes no distinction between password and extension
sessions:

1. Read the `Authorization: Bearer <accessToken>` header; no token → `code:401` (`auth.no.token`). The header is
   the only accepted carrier — the compatibility switch for the `token` query parameter,
   `datapoly.security.token-query-param-enabled`, is off by default.
2. `LoginSessionService.resolve(token)`: the in-process cache first, the database on a miss; an unknown, revoked or
   expired token → `code:401` (`auth.token.invalid`).
3. **Re-read the account row** for the session's username: account gone or `locked` → `code:403`. Role and lock
   state therefore take effect immediately, cache or no cache.
4. Publish the username as the `username` request attribute and let the request through.

Error responses carry an HTTP status equal to `code` (`datapoly.manager.http-status-mapping.enabled`, on by
default); the UI redirects to `/login` on any `code` 401/403.

Two points concern providers directly:

- **The exempt list is hard-coded.** Only `/user/login` (password sign-in) and `/datapoly/manager/api/v1/health/**`
  (the login page fetching the version) are login-related. Endpoints a provider needs the browser to reach while
  the user **has no session yet** — starting the authentication, receiving the identity source's callback — arrive
  without a token and are turned away with a 401 by step 1. The host currently offers no setting or hook for an
  extension to add exempt paths; how those endpoints avoid depending on a session is for the provider to design.
- **The gateway forwards fixed prefixes only.** The gateway (port `8091` by default) is the only externally reachable
  entry, and the prefixes it forwards to manager are `/user/**`, `/datapoly/manager/**`, `/dashboard/**` and the
  static resources. A provider endpoint outside those prefixes cannot be reached from a browser; exposing one means
  adding a gateway route, which is a host change.

### 3.4 Invalidation

Extension sessions share the table and the judgment with password sessions, so every invalidation path the host
already has applies to them automatically — nothing extra to write on the provider side:

| Action | Effect |
| --- | --- |
| `POST /user/logout` (with the session token) | `revoke` deletes the session row; it stops working immediately |
| A user changes their own password | `revokeAll(username, current token)`: every other session of the account dies, the current one is kept |
| `DATAPOLY_ADMIN_PASSWORD` actually changes the admin password | `revokeAll(username)`: every admin session dies |
| The expiry moment is reached | Later requests are refused with 401; the expired row is removed lazily by the next request that presents it, and by the cleanup runs at manager startup and at midnight |

Signing out on the identity source's side (leaving Feishu, an SSO session timing out) does **not** end the DataPoly
session: the host knows nothing about the identity source — a session is the row in `DATAPOLY_LOGIN_TOKEN` and
nothing else. For "signed out there, signed out here", the provider calls `LoginSessionService.revoke` /
`revokeAll` itself.

### 3.5 Do not

- **Do not mint tokens around `issue`.** `TokenUtils.generateValue()` only produces a random string; a token that
  did not go through `issue` has no row in `DATAPOLY_LOGIN_TOKEN` and `resolve` always rejects it.
- **Do not store business objects in `CacheUtils` keyed by token.** `resolve` still honors the
  `token → SystemUserEntity` entries left by extension builds that predate `issue`, for mixed-version deployments
  only; new code always calls `issue`.
- **Do not switch on URL-parameter tokens for convenience** (`datapoly.security.token-query-param-enabled`): a token
  in a URL leaks through Referer headers and proxy access logs. Hand the session token to the front end in the
  response body only; never put it into a redirect URL.
- **Do not count on `LoginGuard`.** The per-account / per-IP rate limit and the consecutive-failure lockout are
  attached to `/user/login` only; the provider's own endpoints are outside them.

## 4. Configuration reference

| Key | Default | Description |
| --- | --- | --- |
| `datapoly.security.login-token-ttl-seconds` | `604800` | Session lifetime in seconds, absolute; environment variable `DATAPOLY_SECURITY_LOGIN_TOKEN_TTL_SECONDS` |
| `datapoly.security.token-query-param-enabled` | `false` | Compatibility switch for the `token` query parameter — keep it off |
| `datapoly.manager.http-status-mapping.enabled` | `true` | Whether error codes are mapped to the HTTP status of the same number |
| `DATAPOLY_FEISHU_ENABLED` | `false` | Feishu login switch (read by the `datapoly-extension-feishu` module, bound as `datapoly.feishu.*`) |
| `DATAPOLY_FEISHU_APP_ID` | empty | App ID of the Feishu app, from the developer console ("Credentials & Basic Info") |
| `DATAPOLY_FEISHU_APP_SECRET` | empty | App Secret of the Feishu app; injected through the environment only |
| `DATAPOLY_FEISHU_REDIRECT_URI` | empty | Redirect URI on the externally reachable gateway address (e.g. `https://<gateway-host>:8091/user/feishu/callback`); must match the "Security settings → Redirect URL" entry in the developer console character for character |

The first three are the host's general session settings; the last four only matter to the Feishu extension — when any
of the three credentials is missing or the switch is off, the login page shows no Feishu entry. Under compose the
values come from the ignored `install/.env`; in a release they go into `conf/config.ini`, where leaving them empty
turns the feature off (`datapolyctl.sh` exports a key only when it has a value — an empty string breaks relaxed
binding of the boolean switch).

## 5. Security notes

- **The extension jar is inside the host's trust domain** (the same trust model as `DataTaskSink` and
  `ApiAssignmentPostProcessor`): the provider runs in the manager process, `issue` puts no restriction on it, and it
  can open a session for any account. Review an extension's origin and permissions as you would a production
  dependency.
- **"Authenticated" is entirely the provider's responsibility.** Verify the identity source's answer on the back end
  (exchange the authorization code with the identity source and confirm the user identity, or verify a signature /
  assertion); never trust an identity — username, employee number, anything — that the browser submits on its own.
- Provisioned accounts keep the default role `USER`; who gets `ADMIN` is an explicit decision made by the provider.
- Real values such as `DATAPOLY_FEISHU_APP_SECRET` are injected through the environment only — never write them back
  into the repository or into an image.

## 6. Troubleshooting FAQ

| Symptom | Cause and fix |
| --- | --- |
| The login page shows no extension entry | When `datapoly-extension/front/src/index.js` is absent, `@extension` falls back to the in-repo empty implementation; or the entry's `loginExtras` is empty. Rebuild the UI (`build-ui.sh`) after changing it — a plain `mvn package` jar carries no UI |
| Clicking the entry does nothing; the page stays on the login form | The provider's pre-session endpoint is stopped by the session interceptor (`code:401`; the UI no longer redirects once it is on `/login`, so nothing visibly happens) — see [3.3](#33-session-interceptor-and-reachable-paths) |
| Signed in, but the first business page bounces back to the login page | `accessToken` was not written to the `token` key in localStorage, or the key name is wrong; see the finish-up code in section 2 |
| The user menu at the top right shows no name | `username` / `realname` were not written; the account-name field of `AccessToken` is `appKey`, not `username` |
| `code:401` "Token does not exist or has expired, please login again" | The session row was deleted (logout, password change) or has expired; sign in again |
| `code:403` "The authenticated user for this token does not exist" | The account row was deleted; if the provider provisions on first sign-in, signing in again recreates it |
| `code:403` "The authenticated user for this token has been locked" | The account's `locked` flag is set; the host has no unlock endpoint, so clear the row's `locked` column in the metadata database |
| A provider endpoint reached through the gateway is not forwarded | The endpoint is not under a prefix the gateway forwards to manager (`/user/**`, `/datapoly/manager/**`, `/dashboard/**`) — see [3.3](#33-session-interceptor-and-reachable-paths) |
| No Feishu entry on the login page | `DATAPOLY_FEISHU_*` is incomplete or the switch is `false`: check `install/.env` under compose, `conf/config.ini` in a release |
| Feishu lands on a failure page after authorizing | `DATAPOLY_FEISHU_REDIRECT_URI` is not configured, or does not match the "Security settings → Redirect URL" entry in the developer console |

## 7. Source index

| Concern | Location |
| --- | --- |
| Front-end entry alias and empty implementation | `datapoly-manager-ui/build/webpack.base.conf.js`, `datapoly-manager-ui/src/extension-stub/index.js` |
| Login-page mount point | `datapoly-manager-ui/src/views/login/index.vue` |
| Login-state storage and request interceptor | `datapoly-manager-ui/src/utils/authStorage.js`, `datapoly-manager-ui/src/assets/http.js` |
| Login response DTO | `datapoly-common/src/main/java/com/cs/common/dto/AccessToken.java` |
| Account row | `datapoly-persistence/src/main/java/com/cs/persistence/dao/SystemUserDao.java`, `datapoly-persistence/src/main/java/com/cs/persistence/entity/SystemUserEntity.java` |
| Session issue / resolve / revoke | `datapoly-core/src/main/java/com/cs/core/service/LoginSessionService.java` |
| Session interceptor | `datapoly-manager/src/main/java/com/cs/manager/config/AuthenticationConfiguration.java` |
| Gateway route table | `datapoly-gateway/src/main/resources/application.yaml` |
| Metadata migrations | `V1_4_0__user-role-ddl.sql`, `V1_7_0__login-token-ddl.sql` under `datapoly-manager/src/main/resources/db/migration/` and `pg/migration/` |
