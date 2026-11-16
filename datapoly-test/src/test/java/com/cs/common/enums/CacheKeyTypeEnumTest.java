// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

public class CacheKeyTypeEnumTest {

    @Test
    public void testCacheFlagAndDescriptions() {
        assertFalse(CacheKeyTypeEnum.NONE.isUseCache());
        assertTrue(CacheKeyTypeEnum.AUTO.isUseCache());
        assertTrue(CacheKeyTypeEnum.SPEL.isUseCache());

        assertEquals("关闭缓存不需要生产缓存KEY", CacheKeyTypeEnum.NONE.getName());
        assertEquals("根据入参自动生成缓存KEY", CacheKeyTypeEnum.AUTO.getName());
        assertEquals("使用SpEL计算生成缓存KEY", CacheKeyTypeEnum.SPEL.getName());
    }
}
