// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.cache.CacheFactory;
import com.cs.cache.DistributedCache;
import com.cs.common.dto.AccessToken;
import com.cs.common.enums.AliveTimeEnum;
import com.cs.common.enums.DurationTimeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.util.TokenUtils;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.AppClientEntity;
import org.junit.*;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-shot token consumption, rotation revocation and null-safe handling (S4):
 * the DB fallback path must consume one-time tokens on verification, and rotating a token must evict the old one.
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

        @Override
        public AppClientEntity getByAccessToken(String accessToken) {
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
    }
}
