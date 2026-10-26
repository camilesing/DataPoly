// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.consts.Constants;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.CacheUtils;
import com.cs.core.service.LoginSessionService;
import com.cs.persistence.dao.LoginTokenDao;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.LoginTokenEntity;
import com.cs.persistence.entity.SystemUserEntity;
import org.junit.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token gate of the manager, exercised with a hand-written request proxy and fake DAOs (no
 * Spring context). Sessions are given the shape they have in production — a row in the login
 * token table — so every case runs through the same path a restarted manager takes.
 */
public class AuthenticationGateTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final String V1 = Constants.MANAGER_API_V1;
    private static final long TTL_SECONDS = 604800L;

    private FakeUserDao userDao;
    private FakeLoginTokenDao loginTokenDao;
    private LoginSessionService loginSessionService;
    private HandlerInterceptor interceptor;

    @Before
    public void setUp() throws Exception {
        CacheUtils.clear();
        userDao = new FakeUserDao();
        loginTokenDao = new FakeLoginTokenDao();
        loginSessionService = new LoginSessionService(loginTokenDao, TTL_SECONDS);
        interceptor = new AuthenticationConfiguration().authenticationInterceptor();
        // the interceptor bean gets its collaborators injected by the container; here by hand
        setField("systemUserDAO", userDao);
        setField("loginSessionService", loginSessionService);
    }

    @Test
    public void missingTokenIsRejected() throws Exception {
        assertCode(ResponseErrorCode.ERROR_TOKEN_EXPIRED, request("GET", V1 + "/data-task/detail/1", null), null);
    }

    @Test
    public void unknownTokenIsRejected() throws Exception {
        persistSession();
        assertCode(ResponseErrorCode.ERROR_TOKEN_EXPIRED,
                request("GET", V1 + "/data-task/detail/1", "9097ac1ab13198dfa4ddb2ecc1079693"), null);
    }

    @Test
    public void inputThatCannotBeATokenIsRejectedWithoutADatabaseLookup() throws Exception {
        persistSession();
        assertCode(ResponseErrorCode.ERROR_TOKEN_EXPIRED,
                request("GET", V1 + "/data-task/detail/1", "not-a-token"), null);
        Assert.assertEquals(0, loginTokenDao.lookups);
    }

    @Test
    public void deletedUserIsRejected() throws Exception {
        persistSession();
        userDao.user = null;
        assertCode(ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, request("GET", V1 + "/data-task/detail/1", TOKEN), null);
    }

    @Test
    public void lockedUserIsRejected() throws Exception {
        persistSession();
        userDao.user.setLocked(Boolean.TRUE);
        assertCode(ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, request("GET", V1 + "/data-task/detail/1", TOKEN), null);
    }

    @Test
    public void openSessionPassesAndPublishesIdentity() throws Exception {
        persistSession();
        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest request = request("POST", V1 + "/data-task/list", TOKEN, attributes);

        Assert.assertTrue(interceptor.preHandle(request, null, handlerMethod()));
        Assert.assertEquals("test", attributes.get("username"));
    }

    @Test
    public void logoutEndsTheSession() throws Exception {
        persistSession();

        loginSessionService.revoke(TOKEN);

        assertCode(ResponseErrorCode.ERROR_TOKEN_EXPIRED, request("GET", V1 + "/data-task/detail/1", TOKEN), null);
    }

    @Test
    public void expiredSessionIsRejectedAndSwept() throws Exception {
        userDao.user = user();
        loginTokenDao.insert(row(currentTimestamp() - 10));

        assertCode(ResponseErrorCode.ERROR_TOKEN_EXPIRED, request("GET", V1 + "/data-task/detail/1", TOKEN), null);
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
        HttpServletRequest request = request("POST", V1 + "/data-task/list", TOKEN, attributes);

        Assert.assertTrue(interceptor.preHandle(request, null, handlerMethod()));
        Assert.assertEquals("test", attributes.get("username"));
    }

    @Test
    public void entryWrittenByAnOlderAccountProviderIsStillAccepted() throws Exception {
        // the pre-persistence format: the token key held the user row itself
        userDao.user = user();
        CacheUtils.put(TOKEN, user());

        Assert.assertTrue(interceptor.preHandle(
                request("GET", V1 + "/data-task/detail/1", TOKEN), null, handlerMethod()));
    }

    @Test
    public void nonHandlerRequestsAreSkipped() throws Exception {
        Assert.assertTrue(interceptor.preHandle(request("GET", V1 + "/data-task/create", null), null, new Object()));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = interceptor.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(interceptor, value);
    }

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

    private static HandlerMethod handlerMethod() {
        try {
            return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertCode(ResponseErrorCode expected, HttpServletRequest request,
                            Map<String, Object> attributes) throws Exception {
        try {
            interceptor.preHandle(request, null, handlerMethod());
            Assert.fail("expected " + expected);
        } catch (CommonException e) {
            Assert.assertEquals(expected, e.getCode());
        }
    }

    private static HttpServletRequest request(String method, String uri, String token) {
        return request(method, uri, token, new HashMap<String, Object>());
    }

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
                    case "getContextPath":
                        return "";
                    case "getCharacterEncoding":
                    case "getPathInfo":
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
                AuthenticationGateTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, handler);
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