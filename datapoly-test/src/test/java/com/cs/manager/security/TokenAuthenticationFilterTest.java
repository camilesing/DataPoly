// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import com.cs.common.util.CacheUtils;
import com.cs.core.service.LoginSessionService;
import com.cs.persistence.dao.LoginTokenDao;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.LoginTokenEntity;
import com.cs.persistence.entity.SystemUserEntity;
import org.junit.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token gate of the manager (filter-chain edition), exercised with hand-written request/response
 * proxies and fake DAOs (no Spring context). Sessions are given the shape they have in production
 * — a row in the login token table — so every case runs through the same path a restarted manager
 * takes. The MCP case pins the reason exempt paths skip the gate entirely: an MCP bearer token is
 * not a login token and must reach the MCP handler unanswered.
 */
public class TokenAuthenticationFilterTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final List<String> SKIP = List.of(
            "/user/login", "/mcp/**", "/index.html", "/favicon.svg", "/static/**", "/js/**", "/css/**",
            "/fonts/**", "/", "/swagger-resources/**", "/swagger-resources", "/swagger-ui.html", "/v2/**",
            "/v3/**", "/swagger-ui/**", "/actuator/**", "/eureka/**", "/error**", "/datapoly/manager/api/v1/health/**");

    private FakeUserDao userDao;
    private FakeLoginTokenDao loginTokenDao;
    private LoginSessionService loginSessionService;
    private TokenAuthenticationFilter filter;

    @Before
    public void setUp() {
        CacheUtils.clear();
        SecurityContextHolder.clearContext();
        userDao = new FakeUserDao();
        loginTokenDao = new FakeLoginTokenDao();
        loginSessionService = new LoginSessionService(loginTokenDao, TTL_SECONDS);
        filter = new TokenAuthenticationFilter(loginSessionService, userDao, SKIP, true);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void missingTokenContinuesAnonymously() throws Exception {
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("GET", "/datapoly/manager/api/v1/data-task/detail/1", null), response().response, chain);
        // The chain decides: the entry point answers protected paths, exempt paths pass through
        Assert.assertTrue(chain.invoked);
        Assert.assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    public void unknownTokenIsRejectedWithUnauthorized() throws Exception {
        persistSession();
        assertRejected(401, "9097ac1ab13198dfa4ddb2ecc1079693");
    }

    @Test
    public void inputThatCannotBeATokenIsRejectedWithoutADatabaseLookup() throws Exception {
        persistSession();
        assertRejected(401, "not-a-token");
        Assert.assertEquals(0, loginTokenDao.lookups);
    }

    @Test
    public void deletedUserIsRejected() throws Exception {
        persistSession();
        userDao.user = null;
        assertRejected(403, TOKEN);
    }

    @Test
    public void lockedUserIsRejected() throws Exception {
        persistSession();
        userDao.user.setLocked(Boolean.TRUE);
        assertRejected(403, TOKEN);
    }

    @Test
    public void openSessionPublishesIdentityAndUserRole() throws Exception {
        persistSession();
        userDao.user.setRole("USER");

        Map<String, Object> attributes = new HashMap<>();
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("POST", "/datapoly/manager/api/v1/data-task/list", TOKEN, attributes),
                response().response, chain);

        Assert.assertTrue(chain.invoked);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Assert.assertEquals("test", authentication.getName());
        Assert.assertTrue(authentication.getAuthorities()
                .contains(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        Assert.assertEquals("test", attributes.get("username"));
    }

    @Test
    public void adminRoleComesFromTheDatabaseRow() throws Exception {
        persistSession();
        userDao.user.setRole("ADMIN");

        filter.doFilter(request("GET", "/datapoly/manager/api/v1/data-task/detail/1", TOKEN),
                response().response, new RecordingChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Assert.assertTrue(authentication.getAuthorities()
                .contains(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    public void dirtyRoleValueFailsClosedToUserRole() throws Exception {
        persistSession();
        userDao.user.setRole("superuser");

        filter.doFilter(request("GET", "/datapoly/manager/api/v1/data-task/detail/1", TOKEN),
                response().response, new RecordingChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Assert.assertTrue(authentication.getAuthorities()
                .contains(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    public void logoutEndsTheSession() throws Exception {
        persistSession();
        loginSessionService.revoke(TOKEN);
        assertRejected(401, TOKEN);
    }

    @Test
    public void expiredSessionIsRejectedAndSwept() throws Exception {
        userDao.user = user();
        loginTokenDao.insert(row(currentTimestamp() - 10));
        assertRejected(401, TOKEN);
        Assert.assertNull(loginTokenDao.findByToken(TOKEN));
    }

    /**
     * The regression this whole persistence change exists for: the in-memory cache of a restarted
     * manager is empty while the session row is not, and the request must still pass.
     */
    @Test
    public void sessionPersistedInTheDatabaseSurvivesARestart() throws Exception {
        persistSession();
        CacheUtils.clear();

        Map<String, Object> attributes = new HashMap<>();
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("POST", "/datapoly/manager/api/v1/data-task/list", TOKEN, attributes),
                response().response, chain);

        Assert.assertTrue(chain.invoked);
        Assert.assertEquals("test", attributes.get("username"));
    }

    @Test
    public void mcpPathsSkipTheLoginGateEntirely() throws Exception {
        // An MCP client token in the Authorization header is not a login token; the MCP handler
        // must see the request (and reject or accept it) itself
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("POST", "/mcp/admin", TOKEN), response().response, chain);
        Assert.assertTrue(chain.invoked);
        Assert.assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    public void loginAndPreflightSkipTheGate() throws Exception {
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("POST", "/user/login", null), response().response, chain);
        Assert.assertTrue(chain.invoked);

        RecordingChain preflight = new RecordingChain();
        filter.doFilter(request("OPTIONS", "/datapoly/manager/api/v1/datasource/list", null),
                response().response, preflight);
        Assert.assertTrue(preflight.invoked);
    }

    private void assertRejected(int expectedStatus, String token) throws Exception {
        FakeResponse fake = response();
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("GET", "/datapoly/manager/api/v1/data-task/detail/1", token),
                fake.response, chain);
        Assert.assertFalse(chain.invoked);
        Assert.assertEquals(expectedStatus, fake.status);
        Assert.assertEquals("application/json;charset=UTF-8", fake.contentType);
        // the ResultEntity body carries the same business code the ExceptionController would
        Assert.assertTrue(fake.body(), fake.body().contains("\"code\":" + expectedStatus));
    }

    private static final long TTL_SECONDS = 604800L;

    /** A session that only exists as a row — the shape every request has after a restart. */
    private void persistSession() {
        userDao.user = user();
        loginTokenDao.insert(row(currentTimestamp() + TTL_SECONDS));
    }

    private static LoginTokenEntity row(long expireAt) {
        return LoginTokenEntity.builder()
                .accessToken(TOKEN)
                .username("test")
                .createAt(currentTimestamp())
                .expireAt(expireAt)
                .build();
    }

    private static long currentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    private static SystemUserEntity user() {
        SystemUserEntity entity = new SystemUserEntity();
        entity.setUsername("test");
        entity.setRole("USER");
        return entity;
    }

    private static HttpServletRequest request(String method, String uri, String token) {
        return request(method, uri, token, new HashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    private static HttpServletRequest request(String method, String uri, String token, Map<String, Object> attributes) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method called, Object[] args) {
                switch (called.getName()) {
                    case "getMethod":
                        return method;
                    case "getRequestURI":
                    case "getServletPath":
                        return uri;
                    case "getPathInfo":
                        return "";
                    case "getContextPath":
                        return "";
                    case "getCharacterEncoding":
                    case "getParameter":
                        return null;
                    case "getHeader":
                        return "Authorization".equalsIgnoreCase(String.valueOf(args[0])) && null != token
                                ? "Bearer " + token
                                : null;
                    case "getAttribute":
                        return attributes.get(args[0]);
                    case "setAttribute":
                        attributes.put((String) args[0], args[1]);
                        return null;
                    case "toString":
                        return "fake-request";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return null;
                }
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                TokenAuthenticationFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, handler);
    }

    private static FakeResponse response() {
        FakeResponse holder = new FakeResponse();
        holder.response = (HttpServletResponse) Proxy.newProxyInstance(
                TokenAuthenticationFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method called, Object[] args) {
                        switch (called.getName()) {
                            case "isCommitted":
                                return false;
                            case "setStatus":
                                holder.status = (Integer) args[0];
                                return null;
                            case "setContentType":
                                holder.contentType = (String) args[0];
                                return null;
                            case "getWriter":
                                return new PrintWriter(holder.buffer, true);
                            case "getCharacterEncoding":
                                return "UTF-8";
                            case "toString":
                                return "fake-response";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return null;
                        }
                    }
                });
        return holder;
    }

    private static final class FakeResponse {
        HttpServletResponse response;
        final StringWriter buffer = new StringWriter();
        int status = 200;
        String contentType;

        String body() {
            return buffer.toString();
        }
    }

    private static final class RecordingChain implements FilterChain {
        boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }

    private static class FakeUserDao extends SystemUserDao {

        private SystemUserEntity user;

        @Override
        public SystemUserEntity findByUsername(String username) {
            return user;
        }
    }

    private static class FakeLoginTokenDao extends LoginTokenDao {

        private final Map<String, LoginTokenEntity> rows = new LinkedHashMap<>();
        private int lookups;

        @Override
        public LoginTokenEntity findByToken(String accessToken) {
            lookups++;
            return rows.get(accessToken);
        }

        @Override
        public void insert(LoginTokenEntity loginToken) {
            rows.put(loginToken.getAccessToken(), loginToken);
        }

        @Override
        public void deleteByToken(String accessToken) {
            rows.remove(accessToken);
        }
    }
}
