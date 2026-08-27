// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.util;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.PageResult.Pagination;
import com.github.pagehelper.PageHelper;

import java.util.*;
import java.util.function.Supplier;

/**
 * Pagination utilities
 */
public final class PageUtils {

    public static <E> PageResult<E> getPage(Supplier<List<E>> method, Integer pageNum, Integer pageSize) {
        if (null == pageNum || pageNum <= 0) {
            pageNum = 1;
        }
        if (null == pageSize || pageSize <= 0) {
            pageSize = Integer.MAX_VALUE;
        }

        com.github.pagehelper.Page<E> originPage = PageHelper.startPage(pageNum, pageSize);
        PageResult<E> resultPage = new PageResult<>();
        resultPage.setData(method.get());

        Pagination pagination = new Pagination();
        pagination.setTotal((int) originPage.getTotal());
        if (0 == originPage.getPageSize()) {
            pagination.setPage(pageNum);
            pagination.setSize(pageSize);
        } else {
            pagination.setPage(originPage.getPageNum());
            pagination.setSize(originPage.getPageSize());
        }
        resultPage.setPagination(pagination);

        return resultPage;
    }

    /**
     * In-memory pagination
     */
    public static <E> PageResult<E> getPage(List<E> pagingList, int pageNum, int pageSize) {
        Pagination pagination = new Pagination();
        PageResult<E> page = new PageResult<>();
        if (pageNum <= 0 || pageSize <= 0) {
            pagination.setTotal(pagingList.size());
            pagination.setPage(0);
            pagination.setSize(0);
            page.setPagination(pagination);
            page.setData(pagingList);
        } else {
            pagination.setTotal(pagingList.size());
            pagination.setPage(pageNum);
            pagination.setSize(pageSize);

            page.setPagination(pagination);
            page.setData(subList(pagingList, pageNum, pageSize));
        }

        return page;
    }

    private static <E> List<E> subList(List<E> list, int pageNum, int pageSize) {
        int size = list.size();
        List<E> result = new ArrayList<>();
        int idx = (pageNum - 1) * pageSize;
        int end = idx + pageSize;
        while (idx < size && idx < end) {
            result.add(list.get(idx));
            idx++;
        }

        return result;
    }

}
