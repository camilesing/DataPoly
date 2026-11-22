# 登录扩展接入指南（第三方账号登录）

语言: [简体中文](login-extension.md) [English](../en/login-extension.md)

管理端内置的登录方式只有账号口令（`POST /user/login`）。"这个人是谁"的判定也可以交给外部：企业 IM、企业 SSO、
OAuth / OIDC 应用等任何能替用户完成身份认证的**账号提供方（account provider）**，都能把自己的入口挂到登录页上。
认证通过后，提供方在宿主里开一个**与口令登录完全等价的会话**——同一张表、同一个有效期、同一道拦截器，此后的
鉴权、登出、改口令踢下线全部沿用宿主既有逻辑。

宿主只提供两样东西：登录页上的一个**挂载点**（第 2 节）和一个**会话签发入口**（第 3 节）。与外部身份源怎么
交互、外部身份怎么对应到本地账号，都由提供方决定。参考实现是飞书登录，位于独立的扩展仓库
`datapoly-extension`（`datapoly-extension-feishu` 模块），不在本仓库内；本文讲的是宿主这一侧的契约。

> 扩展仓库的克隆、构建与装配见 [build-deploy.md](build-deploy.md) 的「宿主扩展装配」：后端模块构建成 jar 投放
> `lib-extra/`、随发行版进入 manager 的 classpath，前端源码经 webpack `@extension` 别名编进管理界面。

## 1、整体时序

以典型的授权码流程为例（与身份源交互的那几步由提供方自行设计）：

```text
浏览器                      外部身份源               提供方（扩展）                宿主 manager
 │ 登录页：口令按钮下方渲染 loginExtras 里的组件
 │ 点击入口 ────────────────▶ 发起认证 ─────────────▶ │
 │ ◀──────────── 认证通过（授权码 / 断言等）────────── │
 │ 把认证结果交给提供方的后端端点 ────────────────────▶ ① 按外部身份找到账号行，没有则开通
 │                                                    │   （DATAPOLY_SYSTEM_USER）
 │                                                  ② LoginSessionService.issue(user)
 │                                                    │   写 DATAPOLY_LOGIN_TOKEN，得到 accessToken
 │ ◀────────────── accessToken 与账号信息 ────────────┘
 │ 写 localStorage（token / username / realname / role），跳转 /dashboard
 │
 │ 此后每个请求带 Authorization: Bearer <accessToken> ───────▶ ③ 会话拦截器：resolve 会话 + 回查账号行
```

①②由提供方实现，③是宿主既有逻辑，口令登录开的会话走的也是 ③。

## 2、前端：登录页挂载点 `loginExtras`

**入口**。管理界面构建时，webpack 把 `@extension` 别名指向 `datapoly-extension/front/src`（入口文件 `index.js`
存在才生效）；该目录不存在时指向仓库内的空实现 `datapoly-manager-ui/src/extension-stub/index.js`。入口默认导出
三个成员：`routes`（扩展页面路由）、`i18n`（词条，深合并进宿主语言包）和 `loginExtras`（登录页组件）。登录扩展
只用到 `loginExtras`：

```js
// datapoly-extension/front/src/index.js
import FeishuLogin from './feishu/FeishuLogin.vue'

export default {
  routes: [],
  loginExtras: [FeishuLogin],       // 登录页：渲染在口令登录按钮下方
  i18n: {
    'zh-CN': { feishu: { login: '使用飞书登录' } },
    'en-US': { feishu: { login: 'Sign in with Feishu' } }
  }
}
```

**挂载点**。`datapoly-manager-ui/src/views/login/index.vue` 在口令登录按钮下方按数组顺序逐个渲染 `loginExtras`
里的组件（`<component :is>`），容器 `.extension-login` 居中、与上方表单以分隔线隔开。数组为空时整块不渲染，
所以不带扩展的纯开源构建登录页没有任何变化。数组在构建期固定（登录页只读一次），运行期不增删——某个提供方
没启用时，由它自己的组件在内部决定不渲染。

**编写约束**。扩展源码与宿主一起编译进同一个 bundle，共用同一个 Vue / Element UI 实例和同一套
`this.$http` / `this.$router` / `this.$t`，也可以直接引用宿主模块（别名 `@` 指向 `datapoly-manager-ui/src`）。
因此不要在扩展自己的 `node_modules` 里再装 `vue`、`element-ui`，否则会打进第二份实例；词条键请带上自己的
命名空间（如上面的 `feishu.*`），不要与宿主键同名。

**登录完成的收尾**。宿主的 `handleLogin` 只处理口令登录，扩展组件拿到提供方后端返回的登录结果后要自己收尾——
与口令登录保持一致，写入同几个键再跳转：

```js
import { setAuth } from '@/utils/authStorage'

// res：this.$http 收到的响应，data 与 /user/login 的响应同形（见 3.2）
const data = res.data.data
setAuth('token', data.accessToken)      // 请求拦截器据此给每个请求附加 Authorization: Bearer
setAuth('username', data.appKey)        // AccessToken 里账号名的字段名是 appKey
setAuth('realname', data.realName)
setAuth('role', data.role || '')
this.$router.push({ path: '/dashboard' })
```

登录态存在 **localStorage**，键集中在 `datapoly-manager-ui/src/utils/authStorage.js`：

- `token` 是唯一影响鉴权的键——`src/assets/http.js` 的请求拦截器读它附加 Bearer 头。宿主没有路由守卫，所谓
  "已登录"就是 localStorage 里有可用的 `token`：没有它，进业务页后第一个接口就会返回 401，响应拦截器随即把页面
  带回 `/login`。
- `username` / `realname` 显示在右上角用户菜单，`username` 另供个人信息页取数。
- `role` 目前只是写入，宿主界面尚不据此裁剪可见范围。
- `version` 可选：口令登录会请求 `/datapoly/manager/api/v1/health/version` 并把 `data` 写进去，供用户菜单显示
  版本号。

## 3、后端：账号提供方

提供方是扩展 jar 里运行在 **manager 进程内**的 Spring 组件。扩展 jar 位于三个服务的公共 classpath，但登录相关
的端点只应落在 manager（网关只把固定前缀转给它，见 [3.3](#33-会话拦截器与可达路径)）；它的 Bean 要在 manager
的组件扫描根之内才会被自动发现（`ManagerApplication.scanBasePackages`，含 `com.cs.manager`、
`com.cs.core.extension` 等），扫描根之外的由扩展模块自己的配置类注册。提供方要做的只有两件事：把外部身份落到
本地账号行，然后调 `LoginSessionService.issue` 开会话。

### 3.1 账号行

账号就是 `DATAPOLY_SYSTEM_USER` 的一行，`username` 在 MySQL / PostgreSQL 下都有唯一索引。查找与开通：

```java
@Resource
private SystemUserDao systemUserDao;               // com.cs.persistence.dao
@Resource
private LoginSessionService loginSessionService;   // com.cs.core.service

SystemUserEntity user = systemUserDao.findByUsername(username);
if (user == null) {
    // 首次登录即开通。password / salt 两列非空，但只被口令登录读取：放一组随机口令的 bcrypt 值，
    // 用户误走口令登录时落到的就是普通的「用户名或密码错误」分支
    String salt = BCrypt.gensalt();                // cn.hutool.crypto.digest.BCrypt
    user = SystemUserEntity.builder()
            .username(username)
            .realName(realName)
            .password(PasswordUtils.encryptPassword(UUID.randomUUID().toString(), salt))  // com.cs.common.util
            .salt(salt)
            .locked(false)
            .build();                              // role 留空即取列默认值 USER
    systemUserDao.insert(user);
}
```

要点：

- **`username` 的取名规则由提供方定，但必须稳定**：同一个外部身份每次都要落到同一行。以身份源的哪个标识为依据
  （open_id、union_id、企业内工号等）要先想清楚，否则同一个人会被开通多个账号，角色与会话各归各的。
- **角色**：`user_role` 列缺省 `USER`。按列定义 `ADMIN` 是管理员（可增删改）、`USER` 是普通用户（只读/调用），但
  角色只决定界面可见范围，鉴权一律以 token 为准。开通时保持缺省，确需管理员再显式赋 `ADMIN`。
- **锁定**：`locked` 为真的账号，已开着的会话也会在下一个请求被拒（见 3.3）。宿主没有加解锁的管理接口，需要时
  直接改元库该账号行的 `locked` 列。

### 3.2 签发会话

`LoginSessionService.issue(user)` 是会话的**唯一签发入口**，口令登录走的也是它：往 `DATAPOLY_LOGIN_TOKEN` 写一行
并返回 `accessToken`。有效期 = 签发时刻 + `datapoly.security.login-token-ttl-seconds`（缺省 604800 = 7 天），
**绝对过期**、不随使用延长；过期时刻在签发时写死，调小配置只影响之后签发的会话（配置改动需重启 manager 生效）。
会话落库，manager 重启不会把人踢下线。

登录结果建议直接复用口令登录的 DTO 与响应包装，前端就能用同一套收尾代码：

```java
String token = loginSessionService.issue(user);
return ResultEntity.success(AccessToken.builder()
        .realName(user.getRealName())
        .appKey(user.getUsername())          // 账号名在 AccessToken 里叫 appKey
        .accessToken(token)
        .createTimestamp(System.currentTimeMillis() / 1000)
        .expireSeconds(loginSessionService.getTtlSeconds())
        .role(user.getRole())
        .build());
```

响应体（`createTimestamp` 不序列化）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "realName": "张三",
    "appKey": "feishu_zhangsan",
    "accessToken": "…",
    "expireSeconds": 604800,
    "role": "USER"
  }
}
```

### 3.3 会话拦截器与可达路径

manager 的会话拦截器（`datapoly-manager/src/main/java/com/cs/manager/config/AuthenticationConfiguration.java`）
覆盖除白名单外的每一个 handler 方法，口令登录与扩展登录开的会话在这里没有区别：

1. 取 `Authorization: Bearer <accessToken>` 头；没有令牌 → `code:401`（`auth.no.token`）。令牌只认这个头，URL
   查询参数 `token` 的兼容开关 `datapoly.security.token-query-param-enabled` 默认关闭。
2. `LoginSessionService.resolve(token)`：进程内缓存未命中时查库；令牌未知、已吊销或已过期 → `code:401`
   （`auth.token.invalid`）。
3. 按会话里的账号名**回查账号行**：账号已不存在或 `locked` 为真 → `code:403`。角色与锁定状态因此即时生效，
   不受缓存影响。
4. 把账号名放进请求属性 `username`，放行。

错误响应的 HTTP 状态与 `code` 相同（`datapoly.manager.http-status-mapping.enabled` 默认开启）；前端收到 `code`
401/403 会自动跳回 `/login`。

两点与提供方直接相关：

- **豁免名单是写死的**：与登录有关的只有 `/user/login`（口令登录）和 `/datapoly/manager/api/v1/health/**`
  （登录页取版本号）。提供方在用户**还没有会话**时就要被浏览器访问的端点——发起认证、接收身份源回调——请求里
  不带令牌，会被第 1 步判成 401。宿主目前没有供扩展追加豁免路径的配置项或钩子，这几个端点如何不依赖会话，
  由提供方自行设计。
- **网关只转发固定前缀**：对外只有 gateway（默认 `8091`），转给 manager 的前缀只有 `/user/**`、
  `/datapoly/manager/**`、`/dashboard/**` 与静态资源。落在这些前缀之外的提供方端点从浏览器访问不到，要开放就得
  在网关新增路由，属于宿主改动。

### 3.4 失效

扩展登录的会话与口令会话同表同判据，宿主既有的失效手段全部自动适用，提供方不必另写：

| 动作 | 效果 |
| --- | --- |
| `POST /user/logout`（带会话令牌） | `revoke` 删除会话行，立即失效 |
| 用户修改自己的口令 | `revokeAll(username, 当前令牌)`：该账号其余会话全部失效，保留当前这一个 |
| `DATAPOLY_ADMIN_PASSWORD` 真的改变了 admin 口令 | `revokeAll(username)`：admin 的全部会话失效 |
| 到达过期时刻 | 之后的请求判为 401；过期行由逐请求的惰性删除与 manager 启动 / 每晚 0 点的清理任务移除 |

外部身份源那边的登出（飞书退出、SSO 会话到期）**不会**让 DataPoly 的会话失效：宿主不感知身份源，会话只认
`DATAPOLY_LOGIN_TOKEN` 里的那一行。需要"身份源登出即踢下线"的效果，由提供方自己调
`LoginSessionService.revoke` / `revokeAll`。

### 3.5 不要这样做

- **不要绕开 `issue` 自己造令牌。** `TokenUtils.generateValue()` 只生成一个随机串，不经 `issue` 的令牌在
  `DATAPOLY_LOGIN_TOKEN` 里没有行，`resolve` 一律判无效。
- **不要按 token 往 `CacheUtils` 里存业务对象。** `resolve` 仍然认旧扩展构建留下的「token → SystemUserEntity」
  条目，只为混部兼容；新代码一律调 `issue`。
- **不要为图方便打开 URL 参数传令牌**（`datapoly.security.token-query-param-enabled`）：令牌会经 Referer 与代理
  访问日志扩散。会话令牌只放在响应体里交给前端，不要放进重定向 URL。
- **不要指望 `LoginGuard`。** 按账号 / IP 限流与连续失败锁定只挂在 `/user/login` 上，提供方自己的端点不在其内。

## 4、配置参考

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `datapoly.security.login-token-ttl-seconds` | `604800` | 会话有效期（秒），绝对过期；环境变量 `DATAPOLY_SECURITY_LOGIN_TOKEN_TTL_SECONDS` |
| `datapoly.security.token-query-param-enabled` | `false` | URL 查询参数 `token` 的兼容开关，保持关闭 |
| `datapoly.manager.http-status-mapping.enabled` | `true` | 错误码是否映射为同名 HTTP 状态 |
| `DATAPOLY_FEISHU_ENABLED` | `false` | 飞书登录开关（`datapoly-extension-feishu` 模块读取，映射为 `datapoly.feishu.*`） |
| `DATAPOLY_FEISHU_APP_ID` | 空 | 飞书应用的 App ID，取自开发者后台「凭证与基础信息」 |
| `DATAPOLY_FEISHU_APP_SECRET` | 空 | 飞书应用的 App Secret，只经环境变量注入 |
| `DATAPOLY_FEISHU_REDIRECT_URI` | 空 | 回调地址，取对外可达的网关地址（如 `https://<网关地址>:8091/user/feishu/callback`），须与开发者后台「安全设置 → 重定向 URL」的登记值逐字一致 |

前三项是宿主通用的会话设置；后四项只对飞书扩展有意义——三项凭证缺一或开关关闭时，登录页不显示飞书入口。compose
部署时取值来自被忽略的 `install/.env`；发行版写在 `conf/config.ini`，留空即关闭（`datapolyctl.sh` 只在键有值
时导出，空串会让布尔开关的宽松绑定失败）。

## 5、安全须知

- **扩展 jar 属宿主信任域**（与 `DataTaskSink`、`ApiAssignmentPostProcessor` 同一信任模型）：提供方运行在
  manager 进程内，`issue` 对它没有任何限制，它能给任何账号开会话。请像对待生产依赖一样评审扩展的来源与权限。
- **"认证通过"完全由提供方负责。** 必须在后端校验身份源的应答（用授权码向身份源换取并核对用户身份，或校验
  签名 / 断言），绝不接受浏览器直接提交的用户名、工号之类自报身份。
- 新开通的账号保持缺省角色 `USER`；把 `ADMIN` 给谁，由提供方显式决定。
- `DATAPOLY_FEISHU_APP_SECRET` 等真实值只经环境变量注入，不要写回仓库或镜像。

## 6、故障排查 FAQ

| 现象 | 原因与处置 |
| --- | --- |
| 登录页没有出现扩展入口 | `datapoly-extension/front/src/index.js` 不存在时 `@extension` 落到仓库内的空实现；或入口的 `loginExtras` 为空。改完要重新构建 UI（`build-ui.sh`），纯 `mvn package` 的 jar 不带 UI |
| 点了入口没有反应，页面停在登录页 | 提供方的前置端点被会话拦截器挡住（`code:401`；已在登录页时前端不再跳转，所以看不到任何变化），见 [3.3](#33-会话拦截器与可达路径) |
| 登录成功后一进业务页就被弹回登录页 | 没把 `accessToken` 写进 localStorage 的 `token` 键，或键名写错；见第 2 节的收尾代码 |
| 右上角用户菜单不显示用户名 | `username` / `realname` 没写；`AccessToken` 里账号名的字段是 `appKey`，不是 `username` |
| `code:401`「Token不存在或已经失效，请重新登录」 | 会话行已被删除（登出、改口令）或已过期，重新登录 |
| `code:403`「Token所使用的认证用户不存在」 | 账号行被删除；提供方若是首次登录自动开通，重新登录即可 |
| `code:403`「Token所使用的认证用户已经被锁定」 | 账号行 `locked` 被置位；宿主没有解锁接口，改元库该行的 `locked` 列 |
| 经网关访问提供方端点，网关不转发 | 端点不在网关转给 manager 的前缀下（`/user/**`、`/datapoly/manager/**`、`/dashboard/**`），见 [3.3](#33-会话拦截器与可达路径) |
| 飞书入口不显示 | `DATAPOLY_FEISHU_*` 缺项或开关为 `false`：compose 看 `install/.env`，发行版看 `conf/config.ini` |
| 飞书授权后跳到失败页 | `DATAPOLY_FEISHU_REDIRECT_URI` 未配置，或与开发者后台「安全设置 → 重定向 URL」的登记值不一致 |

## 7、代码索引

| 关注点 | 位置 |
| --- | --- |
| 前端入口别名与空实现 | `datapoly-manager-ui/build/webpack.base.conf.js`、`datapoly-manager-ui/src/extension-stub/index.js` |
| 登录页挂载点 | `datapoly-manager-ui/src/views/login/index.vue` |
| 登录态存取与请求拦截器 | `datapoly-manager-ui/src/utils/authStorage.js`、`datapoly-manager-ui/src/assets/http.js` |
| 登录响应 DTO | `datapoly-common/src/main/java/com/cs/common/dto/AccessToken.java` |
| 账号行 | `datapoly-persistence/src/main/java/com/cs/persistence/dao/SystemUserDao.java`、`datapoly-persistence/src/main/java/com/cs/persistence/entity/SystemUserEntity.java` |
| 会话签发 / 校验 / 吊销 | `datapoly-core/src/main/java/com/cs/core/service/LoginSessionService.java` |
| 会话拦截器 | `datapoly-manager/src/main/java/com/cs/manager/config/AuthenticationConfiguration.java` |
| 网关路由表 | `datapoly-gateway/src/main/resources/application.yaml` |
| 元库迁移 | `datapoly-manager/src/main/resources/db/migration/` 与 `pg/migration/` 下的 `V1_4_0__user-role-ddl.sql`、`V1_7_0__login-token-ddl.sql` |
