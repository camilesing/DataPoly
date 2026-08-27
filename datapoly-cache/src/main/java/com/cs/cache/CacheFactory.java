// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache;

import java.util.Map;

public interface CacheFactory {

    <T> Map<String, T> getCacheMap(String key, Class<T> clazz);

    DistributedCache getDistributedCache(String name);
}
