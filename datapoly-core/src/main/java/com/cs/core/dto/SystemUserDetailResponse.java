// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "系统用户")
public class SystemUserDetailResponse {

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

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;

}