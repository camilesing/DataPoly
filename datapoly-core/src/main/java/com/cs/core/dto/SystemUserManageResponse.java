// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "系统用户（管理列表项，不含口令与盐）")
public class SystemUserManageResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "登陆名")
    private String username;

    @Schema(description = "实际名")
    private String realName;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "是否锁定")
    private Boolean locked;

    @Schema(description = "角色（ADMIN/USER）")
    private String role;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;
}
