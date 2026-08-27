// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec;

import com.cs.persistence.entity.ApiAssignmentEntity;

public class ApiAssignmentCache {

    private static final ThreadLocal<ApiAssignmentEntity> THREAD_LOCAL = new ThreadLocal<>();

    public static void set(ApiAssignmentEntity entity) {
        THREAD_LOCAL.set(entity);
    }

    public static ApiAssignmentEntity get() {
        return THREAD_LOCAL.get();
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
