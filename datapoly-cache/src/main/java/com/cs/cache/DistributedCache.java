// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache;

import java.util.concurrent.TimeUnit;

/**
 * Distributed cache interface
 */
public interface DistributedCache {

    /**
     * Returns the cache name
     *
     * @return String
     */
    String getName();

    /**
     * Gets the value for the given key
     *
     * @param key  the key
     * @param type the value type
     * @param <T>  the value type
     * @return T
     */
    <T> T get(String key, Class<T> type);

    /**
     * Puts a value into the cache
     *
     * @param key    the key
     * @param value  the value
     * @param expire expiration duration
     * @param unit   time unit of expiration
     */
    void put(String key, Object value, long expire, TimeUnit unit);

    /**
     * Removes the cached value for the given key
     *
     * @param key the key
     */
    void evict(String key);
}
