// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.consts.Constants;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import java.util.Map;

@Slf4j
@UtilityClass
public class PageSizeUtils {

    /**
     * Implicit pagination fallback switch (H4): off by default — queries without declared/passed pagination parameters are no longer silently LIMIT-truncated;
     * set true to restore the old "every query defaults to LIMIT 10" behavior.
     */
    private static final String IMPLICIT_PAGINATION_KEY = "datapoly.executor.pagination.implicit-enabled";

    public static int getPageFromParams(Map<String, Object> params) {
        int page = (null == params.get(Constants.PARAM_PAGE_NUMBER))
                ? 1
                : NumberUtil.parseInt(params.get(Constants.PARAM_PAGE_NUMBER).toString());
        if (page <= 0) {
            page = 1;
        }
        return page;
    }

    public static int getSizeFromParams(Map<String, Object> params) {
        int size = (null == params.get(Constants.PARAM_PAGE_SIZE))
                ? 10
                : NumberUtil.parseInt(params.get(Constants.PARAM_PAGE_SIZE).toString());
        if (size <= 0) {
            size = 10;
        }
        return size;
    }

    /**
     * Whether this is a paginated request (H4): pagination applies when either apiPageNum/apiPageSize has a non-empty value.
     * Pagination is declaration-driven — mergeParameters materializes declared default values into the parameter map, so APIs that declare pagination parameters behave unchanged;
     * judge by value rather than containsKey so a declared-but-empty parameter is not mistaken for pagination.
     */
    public static boolean isPagingRequest(Map<String, Object> params) {
        return null != params.get(Constants.PARAM_PAGE_NUMBER) || null != params.get(Constants.PARAM_PAGE_SIZE);
    }

    /**
     * Whether to append LIMIT/OFFSET (H4): paginated request, or implicit pagination fallback switch enabled.
     * SqlExecutorService is not a Spring bean; switches are read lazily from the Environment (same pattern as ScriptSandboxConfiguration).
     */
    public static boolean shouldAppendPagination(Map<String, Object> params) {
        return isPagingRequest(params) || isImplicitPaginationEnabled();
    }

    private static boolean isImplicitPaginationEnabled() {
        try {
            Environment environment = SpringUtil.getBean(Environment.class);
            String value = environment.getProperty(IMPLICIT_PAGINATION_KEY);
            return Boolean.parseBoolean(StringUtils.trimToEmpty(value));
        } catch (Exception e) {
            return false;
        }
    }

}
