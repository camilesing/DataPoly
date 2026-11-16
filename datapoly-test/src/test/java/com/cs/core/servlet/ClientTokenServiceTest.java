// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.cache.CacheFactory;
import com.cs.cache.DistributedCache;
import com.cs.common.dto.AccessToken;
import com.cs.common.enums.AliveTimeEnum;
import com.cs.common.enums.DurationTimeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.TokenUtils;
import com.cs.core.exec.ExecutorMetadataCache;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.AppClientEntity;
import org.junit.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-shot token consumption, rotation revocation and null-safe handling (S4):
 * the DB fallback path must consume one-time tokens on verification, and rotating a token must evict the old one.
 * Also covers startup rehydration, expiry checks on generate, token-alive semantics and the
 * after-commit cache synchronization.
 */
public class ClientTokenServiceTest {

    private ClientTokenService service;
    private FakeAppClientDao appClientDao;
    private Map<String, AccessToken> cacheMap;

    @Before
    public void setUp() throws Exception {
        service = new ClientTokenService();
        appClientDao = new FakeAppClientDao();
        cacheMap = new ConcurrentHashMap<>();
        CacheFactory cacheFactory = new CacheFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Map<String, T> getCacheMap(String key, Class<T> clazz) {
                return (Map<String, T>) cacheMap;
            }

            @Override
            public DistributedCache getDistributedCache(String name) {
                return null;
            }
        };
        inject("appClientDao", appClientDao);
        inject("cacheFactory", cacheFactory);
    }

    /* ------------------------------ verification ------------------------------ */

    @Test
    public void oneTimeTokenFromDbFallbackIsConsumed() {
        String token = buildToken("once");
        AppClientEntity entity = entity("app-once", DurationTimeEnum.ONLY_ONCE);
        entity.setAccessToken(token);
        appClientDao.byAppKey.put("app-once", entity);
        appClientDao.byAccessToken.put(token, entity);

        Assert.assertEquals("app-once", service.verifyTokenAndGetAppKey(token));
        Assert.assertEquals(Collections.singletonList("app-once"), appClientDao.clearedAppKeys);
        Assert.assertFalse("one-shot token must not be cached", cacheMap.containsKey(token));

        // Second replay: the DB row is consumed, nothing in cache — rejected
        Assert.assertNull(service.verifyTokenAndGetAppKey(token));
    }

    @Test
    public void reusableTokenFromDbFallbackIsCachedNotConsumed() {
        String token = buildToken("reuse");
        AppClientEntity entity = entity("app-reuse", DurationTimeEnum.FOR_EVER);
        entity.setAccessToken(token);
        appClientDao.byAppKey.put("app-reuse", entity);
        appClientDao.byAccessToken.put(token, entity);

        Assert.assertEquals("app-reuse", service.verifyTokenAndGetAppKey(token));
        Assert.assertTrue("reusable token should be cached for later calls", cacheMap.containsKey(token));
        Assert.assertTrue(appClientDao.clearedAppKeys.isEmpty());
        // Second call is served (from the cache path)
        Assert.assertEquals("app-reuse", service.verifyTokenAndGetAppKey(token));
    }

    @Test
    public void blankTokenIsRejectedWithoutLookup() {
        Assert.assertNull(service.verifyTokenAndGetAppKey(null));
        Assert.assertNull(service.verifyTokenAndGetAppKey(""));
        Assert.assertNull(service.verifyTokenAndGetAppKey("   "));
        Assert.assertTrue(appClientDao.byAccessTokenLookups.isEmpty());
    }

    @Test
    public void unknownShortTokenIsRejectedWithoutDbFallback() {
        // only tokens of the exact generated length fall back to the database
        Assert.assertNull(service.verifyTokenAndGetAppKey("short-token"));
        Assert.assertTrue(appClientDao.byAccessTokenLookups.isEmpty());
    }

    @Test
    public void dbFallbackWithUnknownTokenReturnsNull() {
        String token = buildToken("unknown");
        Assert.assertNull(service.verifyTokenAndGetAppKey(token));
        Assert.assertEquals(Collections.singletonList(token), appClientDao.byAccessTokenLookups);
    }

    @Test
    public void dbFallbackWithExpiredTokenIsClearedAndRejected() {
        String token = buildToken("expired");
        AppClientEntity entity = entity("app-exp", DurationTimeEnum.TIME_VALUE);
        entity.setAccessToken(token);
        entity.setExpireAt(System.currentTimeMillis() / 1000L - 60);
        appClientDao.byAppKey.put("app-exp", entity);
        appClientDao.byAccessToken.put(token, entity);

        Assert.assertNull(service.verifyTokenAndGetAppKey(token));
        Assert.assertEquals(Collections.singletonList("app-exp"), appClientDao.clearedAppKeys);
        Assert.assertFalse(cacheMap.containsKey(token));
    }

    @Test
    public void cachedTokenWithNullExpireSecondsIsRejected() {
        cacheMap.put("cached", AccessToken.builder()
                .appKey("app-a").accessToken("cached")
                .createTimestamp(System.currentTimeMillis() / 1000L)
                .expireSeconds(null)
                .build());
        Assert.assertNull(service.verifyTokenAndGetAppKey("cached"));
    }

    @Test
    public void cachedOneShotTokenIsConsumedOnUse() {
        String token = "oneshot-cached";
        AppClientEntity entity = entity("app-c1", DurationTimeEnum.ONLY_ONCE);
        entity.setAccessToken(token);
        appClientDao.byAppKey.put("app-c1", entity);
        cacheMap.put(token, AccessToken.builder()
                .appKey("app-c1").accessToken(token)
                .createTimestamp(System.currentTimeMillis() / 1000L)
                .expireSeconds(0L)
                .build());

        Assert.assertEquals("app-c1", service.verifyTokenAndGetAppKey(token));
        Assert.assertFalse("consumed one-shot entry must leave the cache", cacheMap.containsKey(token));
        Assert.assertEquals(Collections.singletonList("app-c1"), appClientDao.clearedAppKeys);

        // replay: cache empty, DB row cleared
        Assert.assertNull(service.verifyTokenAndGetAppKey(token));
    }

    @Test
    public void cachedLongevityTokenNeverExpires() {
        cacheMap.put("long", AccessToken.builder()
                .appKey("app-long").accessToken("long")
                .createTimestamp(1L)
                .expireSeconds(-1L)
                .build());
        Assert.assertEquals("app-long", service.verifyTokenAndGetAppKey("long"));
        Assert.assertTrue("long-lived token must stay cached", cacheMap.containsKey("long"));
    }

    @Test
    public void cachedTokenPastItsLifetimeIsRejected() {
        long now = System.currentTimeMillis() / 1000L;
        cacheMap.put("stale", AccessToken.builder()
                .appKey("app-stale").accessToken("stale")
                .createTimestamp(now - 100L)
                .expireSeconds(10L)
                .build());
        Assert.assertNull(service.verifyTokenAndGetAppKey("stale"));
    }

    @Test
    public void cachedTokenWithinLifetimeIsAccepted() {
        long now = System.currentTimeMillis() / 1000L;
        cacheMap.put("fresh", AccessToken.builder()
                .appKey("app-fresh").accessToken("fresh")
                .createTimestamp(now)
                .expireSeconds(600L)
                .build());
        Assert.assertEquals("app-fresh", service.verifyTokenAndGetAppKey("fresh"));
    }

    /* -------------------------------- generation -------------------------------- */

    @Test
    public void rotationRevokesOldTokenFromCache() {
        String oldToken = buildToken("old");
        AppClientEntity entity = entity("app-rot", DurationTimeEnum.FOR_EVER);
        entity.setAccessToken(oldToken);
        appClientDao.byAppKey.put("app-rot", entity);
        AccessToken oldCached = AccessToken.builder()
                .appKey("app-rot").accessToken(oldToken)
                .createTimestamp(System.currentTimeMillis() / 1000L)
                .expireSeconds(60L)
                .build();
        cacheMap.put(oldToken, oldCached);

        AccessToken fresh = service.generateToken("app-rot", "sec-app-rot");
        Assert.assertNotEquals(oldToken, fresh.getAccessToken());
        Assert.assertEquals(1, appClientDao.updatedTokens.size());
        Assert.assertFalse("old token must be evicted on rotation", cacheMap.containsKey(oldToken));
        Assert.assertTrue("new token must be cached", cacheMap.containsKey(fresh.getAccessToken()));
    }

    @Test
    public void nullTokenAliveDoesNotThrow() {
        AppClientEntity entity = entity("app-nullalive", DurationTimeEnum.FOR_EVER);
        entity.setTokenAlive(null);
        appClientDao.byAppKey.put("app-nullalive", entity);

        AccessToken token = service.generateToken("app-nullalive", "sec-app-nullalive");
        Assert.assertNotNull(token);
    }

    @Test
    public void timeValueClientWithNullExpireAtIsRejected() {
        AppClientEntity entity = entity("app-noexpire", DurationTimeEnum.TIME_VALUE);
        entity.setExpireAt(null);
        appClientDao.byAppKey.put("app-noexpire", entity);

        try {
            service.generateToken("app-noexpire", "sec-app-noexpire");
            Assert.fail("expected expired client rejection");
        } catch (CommonException expected) {
            // fail-closed on missing expireAt
        }
    }

    @Test
    public void unknownClientIdIsRejected() {
        try {
            service.generateToken("no-such-client", "whatever");
            Assert.fail("expected invalid client rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, e.getCode());
            Assert.assertTrue(String.valueOf(e.getMessage()), e.getMessage().contains("client.id.invalid"));
        }
    }

    @Test
    public void wrongSecretIsRejected() {
        appClientDao.byAppKey.put("app-sec", entity("app-sec", DurationTimeEnum.FOR_EVER));
        try {
            service.generateToken("app-sec", "wrong-secret");
            Assert.fail("expected invalid secret rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, e.getCode());
            Assert.assertTrue(String.valueOf(e.getMessage()), e.getMessage().contains("client.secret.invalid"));
        }
    }

    @Test
    public void timeValueClientAlreadyExpiredIsRejected() {
        AppClientEntity entity = entity("app-past", DurationTimeEnum.TIME_VALUE);
        entity.setExpireAt(System.currentTimeMillis() / 1000L - 5);
        appClientDao.byAppKey.put("app-past", entity);
        try {
            service.generateToken("app-past", "sec-app-past");
            Assert.fail("expected expired client rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, e.getCode());
        }
    }

    @Test
    public void onlyOnceClientAlreadyUsedIsRejected() {
        AppClientEntity entity = entity("app-used", DurationTimeEnum.ONLY_ONCE);
        Timestamp earlier = new Timestamp(System.currentTimeMillis() - 60_000L);
        entity.setCreateTime(earlier);
        appClientDao.byAppKey.put("app-used", entity);
        try {
            service.generateToken("app-used", "sec-app-used");
            Assert.fail("expected one-shot already-used rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, e.getCode());
        }
    }

    @Test
    public void longevityClientReusesPersistedTokenWithoutRotation() {
        String existing = buildToken("kept");
        AppClientEntity entity = entity("app-keep", DurationTimeEnum.FOR_EVER);
        entity.setTokenAlive(AliveTimeEnum.LONGEVITY);
        entity.setAccessToken(existing);
        Timestamp updated = new Timestamp(System.currentTimeMillis() - 5000L);
        entity.setUpdateTime(updated);
        appClientDao.byAppKey.put("app-keep", entity);

        AccessToken token = service.generateToken("app-keep", "sec-app-keep");
        Assert.assertEquals("longevity clients keep their persisted token", existing, token.getAccessToken());
        Assert.assertTrue("no DB update when the token does not rotate", appClientDao.updatedTokens.isEmpty());
        Assert.assertEquals((Long) (updated.getTime() / 1000L), token.getCreateTimestamp());
        Assert.assertEquals((Long) (-1L), token.getExpireSeconds());
        Assert.assertTrue("longevity token stays in the cache", cacheMap.containsKey(existing));
    }

    @Test
    public void zeroExpireSecondsFallsBackToDefaultDuration() {
        AppClientEntity entity = entity("app-zero", DurationTimeEnum.FOR_EVER);
        entity.setTokenAlive(null);
        entity.setExpireAt(System.currentTimeMillis() / 1000L + 5);
        appClientDao.byAppKey.put("app-zero", entity);

        AccessToken token = service.generateToken("app-zero", "sec-app-zero");
        Assert.assertEquals((Long) Long.valueOf(7200L), token.getExpireSeconds());
    }

    @Test
    public void newTokenIsPersistedAndCachedAfterCall() {
        AppClientEntity entity = entity("app-new", DurationTimeEnum.FOR_EVER);
        appClientDao.byAppKey.put("app-new", entity);

        AccessToken token = service.generateToken("app-new", "sec-app-new");
        Assert.assertEquals(1, appClientDao.updatedTokens.size());
        Assert.assertEquals(token.getAccessToken(), appClientDao.updatedTokens.get(0));
        Assert.assertSame(token, cacheMap.get(token.getAccessToken()));
        Assert.assertEquals((Long) Long.valueOf(7200L), token.getExpireSeconds());
    }

    @Test
    public void cacheWriteRunsOnlyAfterTransactionCommit() throws Exception {
        AppClientEntity entity = entity("app-tx", DurationTimeEnum.FOR_EVER);
        appClientDao.byAppKey.put("app-tx", entity);

        TransactionSynchronizationManager.initSynchronization();
        AccessToken token;
        try {
            token = service.generateToken("app-tx", "sec-app-tx");
            Assert.assertFalse("inside the transaction the cache must not be written yet",
                    cacheMap.containsKey(token.getAccessToken()));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            Assert.assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        Assert.assertTrue("after commit the token must be cached", cacheMap.containsKey(token.getAccessToken()));
    }

    @Test
    public void oneTimeClientWithPastExpireAtGeneratesTokenNotCached() {
        AppClientEntity entity = entity("app-ot0", DurationTimeEnum.ONLY_ONCE);
        entity.setExpireAt(System.currentTimeMillis() / 1000L - 5);
        appClientDao.byAppKey.put("app-ot0", entity);

        AccessToken token = service.generateToken("app-ot0", "sec-app-ot0");
        Assert.assertNotNull(token);
        Assert.assertEquals(1, appClientDao.updatedTokens.size());
        // expireSeconds resolves to 0 and the after-commit hook removes instead of caching
        Assert.assertEquals((Long) Long.valueOf(0L), token.getExpireSeconds());
        Assert.assertFalse("zero-expiry one-shot token must not be cached",
                cacheMap.containsKey(token.getAccessToken()));
    }

    @Test
    public void longevityClientWithExpireAtIsCappedFromCreation() {
        AppClientEntity entity = entity("app-cap", DurationTimeEnum.FOR_EVER);
        entity.setTokenAlive(AliveTimeEnum.LONGEVITY);
        entity.setAccessToken(buildToken("cap"));
        long now = System.currentTimeMillis();
        entity.setUpdateTime(new Timestamp(now - 5000L));
        entity.setExpireAt(now / 1000L + 1000);
        appClientDao.byAppKey.put("app-cap", entity);

        AccessToken token = service.generateToken("app-cap", "sec-app-cap");
        // LONGEVITY + expireAt: lifetime computed from creation, not from "now"
        long expected = (now / 1000L + 1000) - ((now - 5000L) / 1000L);
        Assert.assertEquals((Long) expected, token.getExpireSeconds());
    }

    @Test
    public void periodClientWithExpireAtIsCappedToSecondsLeft() {
        AppClientEntity entity = entity("app-left", DurationTimeEnum.FOR_EVER);
        entity.setExpireAt(System.currentTimeMillis() / 1000L + 10);
        appClientDao.byAppKey.put("app-left", entity);

        AccessToken token = service.generateToken("app-left", "sec-app-left");
        Assert.assertEquals((Long) Long.valueOf(10L), token.getExpireSeconds());
    }

    @Test
    public void zeroExpireAtMeansNoExpiryAndFallsBackToDefault() {
        AppClientEntity entity = entity("app-e0", DurationTimeEnum.FOR_EVER);
        entity.setExpireAt(0L);
        appClientDao.byAppKey.put("app-e0", entity);

        AccessToken token = service.generateToken("app-e0", "sec-app-e0");
        Assert.assertEquals((Long) Long.valueOf(7200L), token.getExpireSeconds());
    }

    @Test
    public void longevityClientWithoutTokenGetsFreshOne() {
        AppClientEntity entity = entity("app-fresh", DurationTimeEnum.FOR_EVER);
        entity.setTokenAlive(AliveTimeEnum.LONGEVITY);
        appClientDao.byAppKey.put("app-fresh", entity);

        AccessToken token = service.generateToken("app-fresh", "sec-app-fresh");
        Assert.assertNotNull(token.getAccessToken());
        Assert.assertEquals(1, appClientDao.updatedTokens.size());
        Assert.assertEquals((Long) (-1L), token.getExpireSeconds());
        Assert.assertTrue(cacheMap.containsKey(token.getAccessToken()));
    }

    @Test
    public void dbFallbackWithFutureExpireAtIsAcceptedAndCached() {
        String token = buildToken("future");
        AppClientEntity entity = entity("app-fut", DurationTimeEnum.TIME_VALUE);
        entity.setAccessToken(token);
        entity.setExpireAt(System.currentTimeMillis() / 1000L + 3600);
        appClientDao.byAppKey.put("app-fut", entity);
        appClientDao.byAccessToken.put(token, entity);

        Assert.assertEquals("app-fut", service.verifyTokenAndGetAppKey(token));
        Assert.assertTrue(cacheMap.containsKey(token));
        Assert.assertTrue(appClientDao.clearedAppKeys.isEmpty());
    }

    @Test
    public void verifyAuthGroupBypassesCacheWhenDisabled() throws Exception {
        ExecutorMetadataCache metadataCache = new ExecutorMetadataCache();
        Field enabled = ExecutorMetadataCache.class.getDeclaredField("authGroupEnabled");
        enabled.setAccessible(true);
        enabled.setBoolean(metadataCache, false);
        inject("executorMetadataCache", metadataCache);
        appClientDao.authGroups.put("app-direct", Boolean.TRUE);

        Assert.assertTrue(service.verifyAuthGroup("app-direct", 3L));
        // with the cache disabled the loader runs on every call
        appClientDao.authGroups.put("app-direct", Boolean.FALSE);
        Assert.assertFalse(service.verifyAuthGroup("app-direct", 3L));
        Assert.assertEquals(2, appClientDao.authGroupCalls.size());
    }

    @Test
    public void cachedTokenWithNullCreateTimestampIsTreatedAsFresh() {
        cacheMap.put("notime", AccessToken.builder()
                .appKey("app-nt").accessToken("notime")
                .createTimestamp(null)
                .expireSeconds(600L)
                .build());
        Assert.assertEquals("app-nt", service.verifyTokenAndGetAppKey("notime"));
    }

    @Test
    public void dbFallbackWithNullUpdateTimeUsesCurrentTime() {
        String token = buildToken("notime-db");
        AppClientEntity entity = entity("app-ntdb", DurationTimeEnum.FOR_EVER);
        entity.setAccessToken(token);
        entity.setUpdateTime(null);
        appClientDao.byAppKey.put("app-ntdb", entity);
        appClientDao.byAccessToken.put(token, entity);

        long before = System.currentTimeMillis() / 1000L;
        Assert.assertEquals("app-ntdb", service.verifyTokenAndGetAppKey(token));
        AccessToken cached = cacheMap.get(token);
        Assert.assertNotNull(cached);
        Assert.assertTrue("null updateTime falls back to the current timestamp",
                cached.getCreateTimestamp() >= before);
    }

    @Test
    public void timeValueClientWithFutureExpireAtSucceeds() {
        AppClientEntity entity = entity("app-future", DurationTimeEnum.TIME_VALUE);
        entity.setExpireAt(System.currentTimeMillis() / 1000L + 3600);
        appClientDao.byAppKey.put("app-future", entity);

        AccessToken token = service.generateToken("app-future", "sec-app-future");
        Assert.assertNotNull(token);
        // seconds-left (~3600) is what applies, capped by the PERIOD token-alive value
        long expireSeconds = token.getExpireSeconds();
        Assert.assertTrue("expected ~3600 seconds left, got " + expireSeconds,
                expireSeconds > 3500L && expireSeconds <= 3600L);
    }

    @Test
    public void negativeExpireAtMeansNoAbsoluteExpiry() {
        AppClientEntity entity = entity("app-neg", DurationTimeEnum.FOR_EVER);
        entity.setExpireAt(-1L);
        appClientDao.byAppKey.put("app-neg", entity);

        AccessToken token = service.generateToken("app-neg", "sec-app-neg");
        // negative expireAt is the "no absolute expiry" sentinel: token-alive value applies
        Assert.assertEquals((Long) Long.valueOf(7200L), token.getExpireSeconds());
    }

    /* ------------------------- startup rehydration (init) ------------------------- */

    @Test
    public void initRestoresReusableTokensAndMasksSecrets() {
        String token = buildToken("init");
        AppClientEntity entity = entity("app-init", DurationTimeEnum.FOR_EVER);
        entity.setAccessToken(token);
        appClientDao.allRows.add(entity);
        appClientDao.byAccessToken.put(token, entity);

        service.init();

        Assert.assertTrue("reusable token must be restored into the cache", cacheMap.containsKey(token));
        Assert.assertEquals("app-init", cacheMap.get(token).getAppKey());
        Assert.assertEquals("secret must be masked after rehydration", "******", entity.getAppSecret());
        Assert.assertTrue(appClientDao.clearedAppKeys.isEmpty());
    }

    @Test
    public void initSkipsRowsWithoutToken() {
        AppClientEntity noToken = entity("app-notok", DurationTimeEnum.FOR_EVER);
        appClientDao.allRows.add(noToken);

        service.init();

        Assert.assertTrue(cacheMap.isEmpty());
        Assert.assertTrue(appClientDao.clearedAppKeys.isEmpty());
        Assert.assertEquals("secret still masked for display", "******", noToken.getAppSecret());
    }

    @Test
    public void initDropsTimeValueTokensWithNullExpireAt() {
        String token = buildToken("noexp");
        AppClientEntity entity = entity("app-noexp", DurationTimeEnum.TIME_VALUE);
        entity.setExpireAt(null);
        entity.setAccessToken(token);
        appClientDao.allRows.add(entity);
        appClientDao.byAccessToken.put(token, entity);

        service.init();

        Assert.assertFalse(cacheMap.containsKey(token));
        Assert.assertEquals(Collections.singletonList("app-noexp"), appClientDao.clearedAppKeys);
    }

    @Test
    public void initDropsTimeValueTokensWithZeroExpireAt() {
        String token = buildToken("zeroexp");
        AppClientEntity entity = entity("app-zeroexp", DurationTimeEnum.TIME_VALUE);
        entity.setExpireAt(0L);
        entity.setAccessToken(token);
        appClientDao.allRows.add(entity);
        appClientDao.byAccessToken.put(token, entity);

        service.init();

        Assert.assertFalse(cacheMap.containsKey(token));
        Assert.assertEquals(Collections.singletonList("app-zeroexp"), appClientDao.clearedAppKeys);
    }

    @Test
    public void initDropsExpiredTokensFromPersistence() {
        String token = buildToken("gone");
        AppClientEntity entity = entity("app-gone", DurationTimeEnum.TIME_VALUE);
        entity.setAccessToken(token);
        entity.setExpireAt(System.currentTimeMillis() / 1000L - 10);
        appClientDao.allRows.add(entity);
        appClientDao.byAccessToken.put(token, entity);

        service.init();

        Assert.assertFalse(cacheMap.containsKey(token));
        Assert.assertEquals(Collections.singletonList("app-gone"), appClientDao.clearedAppKeys);
    }

    @Test
    public void initSkipsOneTimeTokens() {
        String token = buildToken("skip");
        AppClientEntity entity = entity("app-skip", DurationTimeEnum.ONLY_ONCE);
        entity.setAccessToken(token);
        appClientDao.allRows.add(entity);
        appClientDao.byAccessToken.put(token, entity);

        service.init();

        Assert.assertFalse("one-time tokens must not be restored", cacheMap.containsKey(token));
        Assert.assertTrue(appClientDao.clearedAppKeys.isEmpty());
    }

    @Test
    public void initRethrowsDaoFailure() {
        appClientDao.listAllFailure = new IllegalStateException("db down");
        try {
            service.init();
            Assert.fail("expected dao failure to propagate");
        } catch (IllegalStateException expected) {
            // init must surface load failures (startup fails loudly)
        }
    }

    /* ------------------------------ auth-group cache ------------------------------ */

    @Test
    public void verifyAuthGroupDelegatesAndCachesPerClientAndGroup() throws Exception {
        ExecutorMetadataCache metadataCache = new ExecutorMetadataCache();
        // plain unit test: @Value defaults are 0 — pin real TTL/size so entries actually cache
        Field ttl = ExecutorMetadataCache.class.getDeclaredField("ttlSeconds");
        ttl.setAccessible(true);
        ttl.setLong(metadataCache, 60L);
        Field maxSize = ExecutorMetadataCache.class.getDeclaredField("maximumSize");
        maxSize.setAccessible(true);
        maxSize.setInt(metadataCache, 100);
        metadataCache.init();
        inject("executorMetadataCache", metadataCache);
        appClientDao.authGroups.put("app-grp", Boolean.TRUE);

        Assert.assertTrue(service.verifyAuthGroup("app-grp", 7L));

        // grant revoked in the DB: within the short TTL the cached value is still served
        appClientDao.authGroups.put("app-grp", Boolean.FALSE);
        Assert.assertTrue("cached grant must be reused within the TTL",
                service.verifyAuthGroup("app-grp", 7L));

        // a different group for the same client is a different cache entry
        Assert.assertFalse(service.verifyAuthGroup("app-grp", 8L));
        Assert.assertEquals(Arrays.asList("app-grp:7", "app-grp:8"), appClientDao.authGroupCalls);
    }

    private AppClientEntity entity(String appKey, DurationTimeEnum duration) {
        AppClientEntity entity = new AppClientEntity();
        entity.setAppKey(appKey);
        entity.setName("name-" + appKey);
        entity.setAppSecret("sec-" + appKey);
        entity.setExpireDuration(duration);
        entity.setTokenAlive(AliveTimeEnum.PERIOD);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private String buildToken(String prefix) {
        int len = TokenUtils.getTokenStringLength();
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < len) {
            sb.append('x');
        }
        return sb.toString();
    }

    private void inject(String name, Object value) throws Exception {
        Field field = ClientTokenService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static class FakeAppClientDao extends AppClientDao {

        private final Map<String, AppClientEntity> byAccessToken = new HashMap<>();
        private final Map<String, AppClientEntity> byAppKey = new HashMap<>();
        private final List<String> clearedAppKeys = new ArrayList<>();
        private final List<String> updatedTokens = new ArrayList<>();
        private final List<AppClientEntity> allRows = new ArrayList<>();
        private final List<String> byAccessTokenLookups = new ArrayList<>();
        private final Map<String, Boolean> authGroups = new HashMap<>();
        private final List<String> authGroupCalls = new ArrayList<>();
        private RuntimeException listAllFailure;

        @Override
        public AppClientEntity getByAccessToken(String accessToken) {
            byAccessTokenLookups.add(accessToken);
            return byAccessToken.get(accessToken);
        }

        @Override
        public AppClientEntity getByAppKey(String appKey) {
            return byAppKey.get(appKey);
        }

        @Override
        public void clearTokenByAppKey(String appKey) {
            clearedAppKeys.add(appKey);
            byAccessToken.clear();
            AppClientEntity entity = byAppKey.get(appKey);
            if (null != entity) {
                entity.setAccessToken(null);
            }
        }

        @Override
        public void updateTokenByAppKey(String appKey, String token) {
            updatedTokens.add(token);
            AppClientEntity entity = byAppKey.get(appKey);
            if (null != entity) {
                entity.setAccessToken(token);
            }
        }

        @Override
        public List<AppClientEntity> listAll() {
            if (null != listAllFailure) {
                throw listAllFailure;
            }
            return allRows;
        }

        @Override
        public boolean existsAuthGroups(String appKey, Long groupId) {
            authGroupCalls.add(appKey + ":" + groupId);
            return Boolean.TRUE.equals(authGroups.get(appKey));
        }
    }
}
