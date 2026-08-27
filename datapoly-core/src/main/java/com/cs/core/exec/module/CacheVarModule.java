// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.cache.*;
import com.cs.common.consts.Constants;
import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
@Module(CacheVarModule.VAR_NAME)
public class CacheVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "cache";

    @Resource
    private CacheFactory cacheFactory;

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    private DistributedCache getDistributedCache() {
        return cacheFactory.getDistributedCache(Constants.CACHE_NAME_API_VAR);
    }

    @Comment("comment.cache.get")
    public String get(@Comment("comment.param.key") String key) {
        DistributedCache cache = getDistributedCache();
        return cache.get(key, String.class);
    }

    @Comment("comment.cache.put")
    public void put(@Comment("comment.param.key") String key, @Comment("comment.param.paramValue") String value, @Comment("comment.param.ttl") long ttl) {
        DistributedCache cache = getDistributedCache();
        cache.put(key, value, ttl, TimeUnit.SECONDS);
    }

    @Comment("comment.cache.evict")
    public void evict(@Comment("comment.param.key") String key) {
        DistributedCache cache = getDistributedCache();
        cache.evict(key);
    }
}
