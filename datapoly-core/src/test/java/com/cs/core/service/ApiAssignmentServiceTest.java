// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.exception.*;
import com.cs.persistence.entity.VersionCommitEntity;
import org.junit.*;

/**
 * Deploy/rollback version ownership validation test (A3): a commit that does not exist or belongs to another API must be rejected,
 * so another API's version content can no longer be deployed/rolled back onto this API, and the NPE 500 is gone.
 */
public class ApiAssignmentServiceTest {

    @Test
    public void rejectNullCommit() {
        try {
            ApiAssignmentService.requireOwnedCommit(1L, 100L, null);
            Assert.fail("Expected CommonException for missing commit.");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void rejectMismatchedCommit() {
        VersionCommitEntity commit = VersionCommitEntity.builder().id(100L).bizId(2L).version(1).build();
        try {
            ApiAssignmentService.requireOwnedCommit(1L, 100L, commit);
            Assert.fail("Expected CommonException for mismatched commit.");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void acceptOwnedCommit() {
        VersionCommitEntity commit = VersionCommitEntity.builder().id(100L).bizId(1L).version(3).build();
        Assert.assertSame(commit, ApiAssignmentService.requireOwnedCommit(1L, 100L, commit));
    }
}
