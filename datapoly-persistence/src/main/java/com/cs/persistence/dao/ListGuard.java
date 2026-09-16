// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import lombok.extern.slf4j.Slf4j;

/**
 * Upper bound for administrative full-list DAO scans (groups, clients, datasources,
 * MCP registries, ...). These lists feed in-memory pagination, so they must not be
 * silently truncated the way the executor data plane forbids implicit LIMITs (H4) —
 * hitting the guard logs a loud WARN so operators archive or narrow instead.
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
