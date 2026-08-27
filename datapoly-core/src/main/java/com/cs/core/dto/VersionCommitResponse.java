// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@ApiModel("版本记录详情")
public class VersionCommitResponse {

    @ApiModelProperty("提交记录ID")
    private Long commitId;

    @ApiModelProperty("版本号")
    private Integer version;

    @ApiModelProperty("版本描述")
    private String description;

    @ApiModelProperty("接口ID")
    private Long apiId;

    @ApiModelProperty("是否在线")
    private Boolean online;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

}
