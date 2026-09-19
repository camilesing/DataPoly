// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import lombok.extern.slf4j.Slf4j;

/**
 * Upper bound for unpaginated full-list DAO scans (api assignment, api online registry).
 * Queries that go through PageHelper (PageUtils.getPage) must NOT append LIMIT_SQL:
 * PageHelper string-appends its own "LIMIT ?" without merging, so a trailing
 * {@code LIMIT 10000 LIMIT ?} fails to parse in MySQL.
 */
@Slf4j
final class ListGuard {

    /**
     * Appended via MyBatis-Plus {@code .last()}; a constant, never user input.
     */
    static final int MAX_ROWS = 10_000;

    static final String LIMIT_SQL = "LIMIT " + MAX_ROWS;

    private ListGuard() {
    }

    static void warnIfHit(String what, int rowCount) {
        if (rowCount >= MAX_ROWS) {
            log.warn("{} list hit the {}-row guard; results may be truncated — archive old rows or narrow the query",
                    what, MAX_ROWS);
        }
    }
}
