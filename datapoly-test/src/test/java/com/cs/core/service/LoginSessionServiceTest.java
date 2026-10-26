// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.util.CacheUtils;
import com.cs.persistence.entity.LoginTokenEntity;
import com.cs.persistence.entity.SystemUserEntity;
import org.junit.*;

/**
 * Lifetime and revocation of manager login sessions, exercised against an in-memory stand-in
 * for the token table (no Spring context). The headline case is the restart: the in-memory
 * cache is dropped, the row is not, and the session must keep working.
 */
public class LoginSessionServiceTest {

    private static final long TTL_SECONDS = 604800L;
    private static final String FIXED_TOKEN = "0123456789abcdef0123456789abcdef";

    private InMemoryLoginTokenDao loginTokenDao;
    private LoginSessionService loginSessionService;

    @Before
    public void setUp() {
        CacheUtils.clear();
        loginTokenDao = new InMemoryLoginTokenDao();
        loginSessionService = new LoginSessionService(loginTokenDao, TTL_SECONDS);
    }

    @Test
    public void issuedSessionIsPersistedWithTheConfiguredLifetime() {
        String token = loginSessionService.issue(user("alice"));

        LoginTokenEntity row = loginTokenDao.findByToken(token);
        Assert.assertNotNull(row);
        Assert.assertEquals("alice", row.getUsername());
        Assert.assertEquals(TTL_SECONDS, row.getExpireAt() - row.getCreateAt());
        Assert.assertNotNull(loginSessionService.resolve(token));
    }

    @Test
    public void sessionSurvivesARestartBecauseTheRowIsTheAuthority() {
        String token = loginSessionService.issue(user("alice"));

        // a restart drops the in-memory cache and nothing else
        CacheUtils.clear();

        LoginSessionService.LoginSession session = loginSessionService.resolve(token);
        Assert.assertNotNull(session);
        Assert.assertEquals("alice", session.getUsername());

        // and the row is only read once: the next request is served from memory again
        int lookups = loginTokenDao.lookups();
        Assert.assertNotNull(loginSessionService.resolve(token));
        Assert.assertEquals(lookups, loginTokenDao.lookups());
    }

    @Test
    public void expiredRowIsRejectedAndDeleted() {
        String token = loginSessionService.issue(user("alice"));
        loginTokenDao.findByToken(token).setExpireAt(currentTimestamp() - 1);
        CacheUtils.clear();

        Assert.assertNull(loginSessionService.resolve(token));
        Assert.assertNull(loginTokenDao.findByToken(token));
    }

    @Test
    public void inMemoryEntryPastItsDeadlineIsRejectedAndEvicted() {
        // With a TTL shorter than the cache's idle window the entry outlives the session: issued
        // 120 seconds ago with a 60 second lifetime, its cache entry is still resident but over
        long issuedAt = currentTimestamp() - 120;
        loginTokenDao.insert(row(FIXED_TOKEN, issuedAt, issuedAt + 60));
        CacheUtils.put(FIXED_TOKEN, new LoginSessionService.LoginSession("alice", issuedAt + 60));

        Assert.assertNull(loginSessionService.resolve(FIXED_TOKEN));
        Assert.assertNull(CacheUtils.get(FIXED_TOKEN));
    }

    @Test
    public void logoutEndsTheSessionImmediately() {
        String token = loginSessionService.issue(user("alice"));

        loginSessionService.revoke(token);

        Assert.assertNull(loginSessionService.resolve(token));
        Assert.assertNull(loginTokenDao.findByToken(token));
        // nothing to resurrect: a fresh process cannot find the row either
        CacheUtils.clear();
        Assert.assertNull(loginSessionService.resolve(token));
    }

    @Test
    public void revokeAllKeepsTheCallersOwnSession() {
        String mine = loginSessionService.issue(user("alice"));
        String other = loginSessionService.issue(user("alice"));
        String bob = loginSessionService.issue(user("bob"));

        loginSessionService.revokeAll("alice", mine);

        Assert.assertNotNull(loginSessionService.resolve(mine));
        Assert.assertNull(loginSessionService.resolve(other));
        Assert.assertNotNull(loginSessionService.resolve(bob));
        // the survivors are still there after a restart; the revoked one is gone for good
        CacheUtils.clear();
        Assert.assertNotNull(loginSessionService.resolve(mine));
        Assert.assertNull(loginSessionService.resolve(other));
        Assert.assertNotNull(loginSessionService.resolve(bob));
    }

    @Test
    public void revokeAllEndsEverySessionOfTheAccount() {
        String first = loginSessionService.issue(user("alice"));
        String second = loginSessionService.issue(user("alice"));
        String bob = loginSessionService.issue(user("bob"));

        loginSessionService.revokeAll("alice");

        CacheUtils.clear();
        Assert.assertNull(loginSessionService.resolve(first));
        Assert.assertNull(loginSessionService.resolve(second));
        Assert.assertNotNull(loginSessionService.resolve(bob));
    }

    @Test
    public void entryWrittenByAnOlderAccountProviderIsHonoredAsASession() {
        // the pre-persistence format: an account provider used to cache the user row under the token
        CacheUtils.put(FIXED_TOKEN, user("alice"));

        LoginSessionService.LoginSession session = loginSessionService.resolve(FIXED_TOKEN);

        Assert.assertNotNull(session);
        Assert.assertEquals("alice", session.getUsername());
    }

    @Test
    public void inputThatCannotBeATokenIsRejectedWithoutADatabaseLookup() {
        Assert.assertNull(loginSessionService.resolve(null));
        Assert.assertNull(loginSessionService.resolve(""));
        Assert.assertNull(loginSessionService.resolve("not-a-token"));
        Assert.assertEquals(0, loginTokenDao.lookups());
    }

    @Test
    public void nonPositiveConfiguredLifetimeFallsBackToTheDefault() {
        Assert.assertEquals(LoginSessionService.DEFAULT_TTL_SECONDS,
                new LoginSessionService(loginTokenDao, 0).getTtlSeconds());
        Assert.assertEquals(LoginSessionService.DEFAULT_TTL_SECONDS,
                new LoginSessionService(loginTokenDao, -1).getTtlSeconds());
    }

    private static SystemUserEntity user(String username) {
        SystemUserEntity entity = new SystemUserEntity();
        entity.setUsername(username);
        entity.setRole("USER");
        return entity;
    }

    private static LoginTokenEntity row(String token, long createAt, long expireAt) {
        return LoginTokenEntity.builder()
                .accessToken(token)
                .username("alice")
                .createAt(createAt)
                .expireAt(expireAt)
                .build();
    }

    private static long currentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }
}