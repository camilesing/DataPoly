// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec;

import com.cs.common.enums.HttpMethodEnum;
import com.cs.persistence.entity.*;
import com.google.common.base.Ticker;
import com.google.common.cache.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Local short-TTL metadata cache for executor (A2): eliminates per-request SELECTs against the metadata DB.
 *
 * <p>Caches three kinds of queries: api_online (method+path), datasource (id), and auth-group checks (appKey+groupId);
 * negative results (null) are cached too (offline/missing APIs no longer hit the DB on 404). Load failures are not cached —
 * cached entries remain usable within the TTL window if the metadata DB goes down, while uncached entries fail as they would without the cache.
 *
 * <p>Semantic constraint: expireAfterWrite (not access) is required to guarantee "deploys/offline/auth changes take effect within one TTL at most";
 * entities are read-only on the request path (HttpApiServlet/engines/script modules do not mutate them), so cached instances can be shared directly.
 * Used only by the executor process (manager/gateway do not scan the corresponding integration points); see AGENTS.md phase-3 section for change-visibility semantics.
 */
@Component
public class ExecutorMetadataCache {

    // Fields package-visible for test overrides (same precedent as GatewaySourceFilter); default true so it stays enabled in plain unit tests without Spring injection
    @Value("${datapoly.executor.metadata-cache.enabled:true}")
    boolean enabled = true;

    @Value("${datapoly.executor.metadata-cache.ttl-seconds:10}")
    private long ttlSeconds;

    @Value("${datapoly.executor.metadata-cache.maximum-size:2000}")
    private int maximumSize;

    @Value("${datapoly.executor.metadata-cache.auth-group-enabled:true}")
    boolean authGroupEnabled = true;

    private Cache<String, Optional<ApiAssignmentEntity>> apiAssignmentCache;
    private Cache<Long, Optional<DataSourceEntity>> datasourceCache;
    private Cache<String, Boolean> authGroupCache;

    @PostConstruct
    public void init() {
        rebuild(ttlSeconds, maximumSize, Ticker.systemTicker());
    }

    /**
     * For tests to inject a Ticker and rebuild the cache from parameters (production path calls {@link #init()}).
     */
    void rebuild(long ttlSeconds, int maximumSize, Ticker ticker) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maximumSize)
                .ticker(ticker);
        this.apiAssignmentCache = builder.build();
        this.datasourceCache = builder.build();
        this.authGroupCache = builder.build();
    }

    public ApiAssignmentEntity getApiAssignment(HttpMethodEnum method, String path,
                                                Supplier<ApiAssignmentEntity> loader) {
        if (!enabled || null == apiAssignmentCache) {
            return loader.get();
        }
        Optional<ApiAssignmentEntity> cached = apiAssignmentCache.getIfPresent(buildKey(method, path));
        if (null != cached) {
            return cached.orElse(null);
        }
        ApiAssignmentEntity value = loader.get();
        apiAssignmentCache.put(buildKey(method, path), Optional.ofNullable(value));
        return value;
    }

    public DataSourceEntity getDataSource(Long id, Supplier<DataSourceEntity> loader) {
        if (!enabled || null == datasourceCache) {
            return loader.get();
        }
        Optional<DataSourceEntity> cached = datasourceCache.getIfPresent(id);
        if (null != cached) {
            return cached.orElse(null);
        }
        DataSourceEntity value = loader.get();
        datasourceCache.put(id, Optional.ofNullable(value));
        return value;
    }

    public boolean getAuthGroup(String appKey, Long groupId, Supplier<Boolean> loader) {
        if (!authGroupEnabled || null == authGroupCache) {
            return Boolean.TRUE.equals(loader.get());
        }
        String key = appKey + ":" + groupId;
        Boolean cached = authGroupCache.getIfPresent(key);
        if (null != cached) {
            return cached;
        }
        boolean value = Boolean.TRUE.equals(loader.get());
        authGroupCache.put(key, value);
        return value;
    }

    private String buildKey(HttpMethodEnum method, String path) {
        return method.name() + ":" + path;
    }

}
