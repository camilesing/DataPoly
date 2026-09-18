// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "版本记录详情")
public class VersionCommitResponse {

    @Schema(description = "提交记录ID")
    private Long commitId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "版本描述")
    private String description;

    @Schema(description = "接口ID")
    private Long apiId;

    @Schema(description = "是否在线")
    private Boolean online;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

}
