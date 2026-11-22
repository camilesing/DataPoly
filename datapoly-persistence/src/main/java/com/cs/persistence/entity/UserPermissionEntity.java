// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/**
 * One cell of the per-user permission matrix: the exact action set a USER-role account has on one
 * {@link com.cs.common.consts.PermissionModule}. ADMIN never consults rows; a USER without a row
 * for a module falls back to the read-only default. Rows are re-read per request, like the account
 * row, so a grant or revocation applies to the next request without cache invalidation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_USER_PERMISSION", autoResultMap = true)
public class UserPermissionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** Account name, matching DATAPOLY_SYSTEM_USER.username. */
    @TableField("username")
    private String username;

    /** {@link com.cs.common.consts.PermissionModule} name. */
    @TableField("module")
    private String module;

    /** Comma set of {@link com.cs.common.consts.PermissionAction} names, e.g. {@code "R,C,U"}. */
    @TableField("actions")
    private String actions;

    /** Grant time, epoch seconds. */
    @TableField("create_at")
    private Long createAt;
}
