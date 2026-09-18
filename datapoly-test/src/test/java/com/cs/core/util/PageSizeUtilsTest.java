// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.consts.Constants;
import org.junit.*;

import java.util.*;

/**
 * Pagination semantics test (H4): queries without declared/passed pagination parameters are no longer silently LIMIT-truncated;
 * requests that pass apiPageNum/apiPageSize (or declare defaults) are still paginated.
 */
public class PageSizeUtilsTest {

    @Test
    public void pagingRequestWithoutPageParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "tom");
        Assert.assertFalse(PageSizeUtils.isPagingRequest(params));
    }

    @Test
    public void pagingRequestWithPageNumber() {
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_PAGE_NUMBER, 2);
        Assert.assertTrue(PageSizeUtils.isPagingRequest(params));
    }

    @Test
    public void pagingRequestWithPageSizeOnly() {
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_PAGE_SIZE, "20");
        Assert.assertTrue(PageSizeUtils.isPagingRequest(params));
    }

    @Test
    public void pagingRequestWithNullValueParams() {
        // A declared parameter with an empty value (not passed and no default) does not count as a paginated request
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_PAGE_NUMBER, null);
        params.put(Constants.PARAM_PAGE_SIZE, null);
        Assert.assertFalse(PageSizeUtils.isPagingRequest(params));
    }

    @Test
    public void shouldNotAppendPaginationByDefault() {
        // No pagination parameters and the implicit pagination switch unconfigured (default off, false fallback without Spring): no pagination appended
        Map<String, Object> params = new HashMap<>();
        Assert.assertFalse(PageSizeUtils.shouldAppendPagination(params));
    }

    @Test
    public void shouldAppendPaginationForPagingRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.PARAM_PAGE_SIZE, 20);
        Assert.assertTrue(PageSizeUtils.shouldAppendPagination(params));
    }

    @Test
    public void pageParamsStillApplyDefaults() {
        Map<String, Object> params = new HashMap<>();
        Assert.assertEquals(1, PageSizeUtils.getPageFromParams(params));
        Assert.assertEquals(10, PageSizeUtils.getSizeFromParams(params));
    }
}
