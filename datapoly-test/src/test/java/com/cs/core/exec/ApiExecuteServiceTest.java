// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec;

import org.junit.*;

/**
 * Response cache key versioning test (A5): the cache key includes the online commitId, so old cache keys become unreachable after deploying a new version.
 */
public class ApiExecuteServiceTest {

    @Test
    public void cacheKeyChangesWithCommitId() {
        String v11 = ApiExecuteService.buildCacheKey("GET:/api/user", 11L, "abc");
        String v12 = ApiExecuteService.buildCacheKey("GET:/api/user", 12L, "abc");
        Assert.assertEquals("GET:/api/user:11:abc", v11);
        Assert.assertNotEquals(v11, v12);
    }

    @Test
    public void cacheKeyStableForSameCommit() {
        Assert.assertEquals(
                ApiExecuteService.buildCacheKey("GET:/api/user", 11L, "abc"),
                ApiExecuteService.buildCacheKey("GET:/api/user", 11L, "abc"));
    }

    @Test
    public void cacheKeyDefensiveForNullCommitId() {
        // Falls back to 0 on non-online paths (no commitId overlay), without throwing
        Assert.assertEquals("GET:/api/user:0:abc", ApiExecuteService.buildCacheKey("GET:/api/user", null, "abc"));
    }
}
