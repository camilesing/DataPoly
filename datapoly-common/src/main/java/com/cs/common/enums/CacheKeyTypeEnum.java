// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

public enum CacheKeyTypeEnum {

    NONE(false, "关闭缓存不需要生产缓存KEY"),
    AUTO(true, "根据入参自动生成缓存KEY"),
    SPEL(true, "使用SpEL计算生成缓存KEY"),
    ;

    private boolean useCache;
    private String name;

    CacheKeyTypeEnum(boolean cache, String name) {
        this.useCache = cache;
        this.name = name;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public String getName() {
        return name;
    }
}
