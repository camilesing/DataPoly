// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/**
 * One persisted manager login session. The row is the authority: it is what makes a login
 * survive a process restart, and deleting it is what ends the session (logout, password
 * change, expiry). {@code createAt}/{@code expireAt} are epoch seconds so the MySQL and
 * PostgreSQL flavors compare on one basis, exactly like {@link AppClientEntity#getExpireAt()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_LOGIN_TOKEN", autoResultMap = true)
public class LoginTokenEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** Session token as handed to the client (the login response's {@code accessToken}). */
    @TableField("access_token")
    private String accessToken;

    /** Account name, matching DATAPOLY_SYSTEM_USER.username. */
    @TableField("username")
    private String username;

    /** Issue time, epoch seconds. */
    @TableField("create_at")
    private Long createAt;

    /** Absolute expiry, epoch seconds; never extended by activity. */
    @TableField("expire_at")
    private Long expireAt;
}