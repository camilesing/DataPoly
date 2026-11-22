// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.cache.CacheFactory;
import com.cs.cache.DistributedCache;
import com.cs.common.dto.AccessToken;
import com.cs.common.dto.PageResult;
import com.cs.common.dto.IdWithName;
import com.cs.common.enums.*;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.AppClientDetailResponse;
import com.cs.core.dto.AppClientSaveRequest;
import com.cs.core.dto.AppClientSearchRequest;
import com.cs.core.dto.EntityIdNameResponse;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.AppClientEntity;
import com.github.pagehelper.PageHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * App client lifecycle: create derives the expiry semantics from the chosen {@link ExpireTimeEnum},
 * delete evicts a live token from the token cache, and the search list renders expiry state.
 */
public class AppClientServiceTest {

    private static final String CACHE_KEY = com.cs.common.consts.Constants.CACHE_KEY_TOKEN_CLIENT;

    private FakeAppClientDao dao;
    private FakeCacheFactory cacheFactory;
    private AppClientService service;

    @Before
    public void setUp() {
        dao = new FakeAppClientDao();
        cacheFactory = new FakeCacheFactory();
        service = new AppClientService();
        DataTaskTestSupport.setField(service, "appClientDao", dao);
        DataTaskTestSupport.setField(service, "cacheFactory", cacheFactory);
    }

    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // create
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void createRejectsDuplicateAppKey() {
        dao.byAppKey = entity("ak", "name");
        try {
            service.create(saveRequest("ak", "other", ExpireTimeEnum.EXPIRE_01_DAY));
            fail("duplicate app key must be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        assertTrue(dao.inserted.isEmpty());
    }

    @Test
    public void createRejectsDuplicateName() {
        dao.byName = Collections.singletonList(entity("other", "name"));
        try {
            service.create(saveRequest("ak", "name", ExpireTimeEnum.EXPIRE_01_DAY));
            fail("duplicate name must be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        assertTrue(dao.inserted.isEmpty());
    }

    @Test
    public void createTimeValueExpiryUsesNowPlusDuration() {
        long before = System.currentTimeMillis() / 1000;
        service.create(saveRequest("ak", "app", ExpireTimeEnum.EXPIRE_30_MIN));
        long after = System.currentTimeMillis() / 1000;

        assertEquals(1, dao.inserted.size());
        AppClientEntity saved = dao.inserted.get(0);
        assertEquals(DurationTimeEnum.TIME_VALUE, saved.getExpireDuration());
        assertEquals(ExpireTimeEnum.EXPIRE_30_MIN.getValue() + before, saved.getExpireAt().longValue());
        assertEquals(ExpireTimeEnum.EXPIRE_30_MIN.getValue() + after, saved.getExpireAt().longValue());
        assertNull("a fresh client carries no token", saved.getAccessToken());
        assertNotNull("the app secret is generated server side", saved.getAppSecret());
        assertFalse(saved.getAppSecret().isEmpty());
        assertEquals("app", saved.getName());
    }

    @Test
    public void createForeverExpiryIsMinusOne() {
        service.create(saveRequest("ak", "app", ExpireTimeEnum.EXPIRE_FOR_EVER));
        assertEquals(Long.valueOf(-1L), dao.inserted.get(0).getExpireAt());
        assertEquals(DurationTimeEnum.FOR_EVER, dao.inserted.get(0).getExpireDuration());
    }

    @Test
    public void createOnceExpiryIsZero() {
        service.create(saveRequest("ak", "app", ExpireTimeEnum.EXPIRE_ONLY_ONCE));
        assertEquals(Long.valueOf(0L), dao.inserted.get(0).getExpireAt());
        assertEquals(DurationTimeEnum.ONLY_ONCE, dao.inserted.get(0).getExpireDuration());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // delete
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void deleteMissingClientIsNoOp() {
        dao.byId = null;
        service.delete(1L);
        assertTrue(dao.deleted.isEmpty());
        assertTrue("no token eviction without a row", cacheFactory.tokenCache.isEmpty());
    }

    @Test
    public void deleteWithoutLiveTokenSkipsCache() {
        dao.byId = entity("ak", "app");
        service.delete(1L);
        assertEquals(Collections.singletonList(1L), dao.deleted);
        assertTrue(cacheFactory.tokenCache.isEmpty());
    }

    @Test
    public void deleteEvictsLiveTokenFromCache() {
        dao.byId = entity("ak", "app");
        dao.byId.setId(7L);
        dao.byId.setAccessToken("live-token");
        cacheFactory.tokenCache.put("live-token", AccessToken.builder().accessToken("live-token").build());

        service.delete(7L);

        assertEquals(Collections.singletonList(7L), dao.deleted);
        assertFalse("the token cache entry must be evicted with the row",
                cacheFactory.tokenCache.containsKey("live-token"));
        assertEquals(Collections.singletonList(CACHE_KEY), cacheFactory.requestedCacheKeys);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // searchList
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void searchListRendersExpiryStatePerRow() {
        AppClientEntity expired = entity("ak-1", "expired");
        expired.setExpireDuration(DurationTimeEnum.TIME_VALUE);
        expired.setExpireAt(System.currentTimeMillis() / 1000 - 3600);

        AppClientEntity forever = entity("ak-2", "forever");
        forever.setExpireDuration(DurationTimeEnum.FOR_EVER);
        forever.setExpireAt(-1L);

        AppClientEntity future = entity("ak-3", "future");
        future.setExpireDuration(DurationTimeEnum.TIME_VALUE);
        future.setExpireAt(System.currentTimeMillis() / 1000 + 86400);

        dao.searchResult = Arrays.asList(expired, forever, future);
        AppClientSearchRequest request = new AppClientSearchRequest();
        request.setSearchText("app");
        request.setGroupId(4L);
        request.setPage(1);
        request.setSize(10);

        PageResult<AppClientDetailResponse> page;
        try {
            page = service.searchList(request);
        } finally {
            PageHelper.clearPage();
        }

        assertEquals("search filters pass through", "app", dao.searchedText);
        assertEquals(Long.valueOf(4L), dao.searchedGroup);
        List<AppClientDetailResponse> rows = page.getData();
        assertEquals(3, rows.size());
        assertTrue("a TIME_VALUE row past its stamp is expired", rows.get(0).getIsExpired());
        assertFalse("FOR_EVER rows never expire", rows.get(1).getIsExpired());
        assertFalse(rows.get(2).getIsExpired());
        assertNull("non-positive expiry has no formatted stamp", rows.get(1).getExpireAtStr());
        assertNotNull("a future absolute expiry is rendered", rows.get(2).getExpireAtStr());
        assertTrue(rows.get(2).getExpireAtStr().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals("an ad-hoc stamp resolves to the unknown kind",
                ExpireTimeEnum.EXPIRE_UNKNOWN.getDescription(), rows.get(0).getExpireType());
    }

    @Test
    public void searchListRecognizesKnownExpiryKinds() {
        AppClientEntity known = entity("ak", "app");
        known.setExpireDuration(DurationTimeEnum.TIME_VALUE);
        known.setExpireAt(ExpireTimeEnum.EXPIRE_01_DAY.getValue());
        dao.searchResult = Collections.singletonList(known);
        AppClientSearchRequest request = new AppClientSearchRequest();
        request.setPage(1);
        request.setSize(10);

        PageResult<AppClientDetailResponse> page;
        try {
            page = service.searchList(request);
        } finally {
            PageHelper.clearPage();
        }

        // a stored (duration, expireAt) pair matching a catalog entry resolves to that entry
        String expireType = page.getData().get(0).getExpireType();
        assertNotNull(expireType);
        assertEquals(ExpireTimeEnum.EXPIRE_01_DAY.getDescription(), expireType);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // getSecret / group auth
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void getSecretRequiresExistingRow() {
        dao.byId = null;
        try {
            service.getSecret(1L);
            fail("missing client must be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void getSecretReturnsStoredSecret() {
        dao.byId = entity("ak", "app");
        dao.byId.setAppSecret("s3cret");
        assertEquals("s3cret", service.getSecret(1L));
    }

    @Test
    public void createGroupAuthSavesTheSelection() {
        com.cs.core.dto.AppClientGroupRequest request = new com.cs.core.dto.AppClientGroupRequest();
        request.setId(7L);
        request.setGroupIds(Arrays.asList(1L, 2L));
        service.createGroupAuth(request);
        assertEquals(1, dao.savedAuth.size());
        assertEquals(Long.valueOf(7L), dao.savedAuth.get(0)[0]);
        assertEquals(Arrays.asList(1L, 2L), dao.savedAuth.get(0)[1]);
    }

    @Test
    public void getGroupAuthMapsIdNamePairs() {
        dao.groupAuth = Arrays.asList(new IdWithName(1L, "core"), new IdWithName(2L, "orders"));
        List<EntityIdNameResponse> auth = service.getGroupAuth(7L);
        assertEquals(2, auth.size());
        assertEquals(Long.valueOf(1L), auth.get(0).getId());
        assertEquals("core", auth.get(0).getName());
        assertEquals("orders", auth.get(1).getName());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // fixtures
    /////////////////////////////////////////////////////////////////////////////////////

    private static AppClientEntity entity(String appKey, String name) {
        AppClientEntity entity = AppClientEntity.builder().appKey(appKey).name(name).build();
        entity.setId(1L);
        return entity;
    }

    private static AppClientSaveRequest saveRequest(String appKey, String name, ExpireTimeEnum expireTime) {
        AppClientSaveRequest request = new AppClientSaveRequest();
        request.setAppKey(appKey);
        request.setName(name);
        request.setExpireTime(expireTime);
        request.setTokenAlive(AliveTimeEnum.PERIOD);
        return request;
    }

    private static final class FakeAppClientDao extends AppClientDao {
        AppClientEntity byId;
        AppClientEntity byAppKey;
        List<AppClientEntity> byName = Collections.emptyList();
        List<AppClientEntity> searchResult = Collections.emptyList();
        List<IdWithName> groupAuth = Collections.emptyList();
        final List<AppClientEntity> inserted = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Object[]> savedAuth = new ArrayList<>();
        String searchedText;
        Long searchedGroup;

        @Override
        public AppClientEntity getById(Long id) {
            return byId;
        }

        @Override
        public AppClientEntity getByAppKey(String appKey) {
            return byAppKey;
        }

        @Override
        public List<AppClientEntity> getByName(String name) {
            return byName;
        }

        @Override
        public void insert(AppClientEntity entity) {
            inserted.add(entity);
        }

        @Override
        public void deleteById(Long id) {
            deleted.add(id);
        }

        @Override
        public List<AppClientEntity> listAll(String searchText, Long groupId) {
            searchedText = searchText;
            searchedGroup = groupId;
            return searchResult;
        }

        @Override
        public void saveAuthGroup(Long id, List<Long> groupIds) {
            savedAuth.add(new Object[]{id, groupIds});
        }

        @Override
        public List<IdWithName> getGroupAuth(Long id) {
            return groupAuth;
        }
    }

    private static final class FakeCacheFactory implements CacheFactory {
        final Map<String, AccessToken> tokenCache = new HashMap<>();
        final List<String> requestedCacheKeys = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> Map<String, T> getCacheMap(String key, Class<T> clazz) {
            requestedCacheKeys.add(key);
            return (Map<String, T>) tokenCache;
        }

        @Override
        public DistributedCache getDistributedCache(String name) {
            return null;
        }
    }
}
