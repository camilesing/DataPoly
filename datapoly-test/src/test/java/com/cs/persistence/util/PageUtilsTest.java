// Use of this source code is governed by a BSD-style license
package com.cs.persistence.util;

import com.cs.common.dto.PageResult;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class PageUtilsTest {

    @After
    public void clearPageHelper() {
        com.github.pagehelper.PageHelper.clearPage();
    }

    @Test
    public void testInMemoryPaginationSlicesList() {
        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            data.add(i);
        }
        PageResult<Integer> page = PageUtils.getPage(data, 2, 10);
        assertEquals(25, page.getPagination().getTotal());
        assertEquals(2, page.getPagination().getPage());
        assertEquals(10, page.getPagination().getSize());
        assertEquals(Arrays.asList(11, 12, 13, 14, 15, 16, 17, 18, 19, 20), page.getData());
    }

    @Test
    public void testInMemoryPaginationLastPartialPage() {
        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            data.add(i);
        }
        PageResult<Integer> page = PageUtils.getPage(data, 3, 10);
        assertEquals(Arrays.asList(21, 22, 23, 24, 25), page.getData());
    }

    @Test
    public void testInMemoryPaginationBeyondEndIsEmpty() {
        PageResult<Integer> page = PageUtils.getPage(Arrays.asList(1, 2, 3), 5, 10);
        assertTrue(page.getData().isEmpty());
        assertEquals(3, page.getPagination().getTotal());
    }

    @Test
    public void testInMemoryPaginationInvalidInputReturnsWholeList() {
        PageResult<Integer> page = PageUtils.getPage(Arrays.asList(1, 2, 3), 0, 0);
        assertEquals(Arrays.asList(1, 2, 3), page.getData());
        assertEquals(0, page.getPagination().getPage());
        assertEquals(0, page.getPagination().getSize());
    }

    @Test
    public void testPageHelperVariantWrapsSupplierResult() {
        PageResult<String> page = PageUtils.getPage(() -> Arrays.asList("a", "b"), 1, 10);
        assertEquals(Arrays.asList("a", "b"), page.getData());
        assertEquals(1, page.getPagination().getPage());
        assertEquals(10, page.getPagination().getSize());
    }

    @Test
    public void testPageHelperVariantDefaultsInvalidPaging() {
        PageResult<String> page = PageUtils.getPage(() -> Arrays.asList("a"), null, 0);
        // page defaults to 1; size defaults to Integer.MAX_VALUE
        assertEquals(1, page.getPagination().getPage());
        assertEquals(Integer.MAX_VALUE, page.getPagination().getSize());
        assertEquals(Arrays.asList("a"), page.getData());
    }
}
