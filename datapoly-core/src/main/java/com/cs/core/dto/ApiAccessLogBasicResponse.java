// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("API调用日志记录")
public class ApiAccessLogBasicResponse {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("HTTP状态码")
    private Integer status;

    @ApiModelProperty("耗时")
    private Long duration;

    @ApiModelProperty("客户端地址")
    private String ipAddr;

    @ApiModelProperty("客户端UA")
    private String userAgent;

    @ApiModelProperty("应用名称")
    private String clientApp;

    @ApiModelProperty("请求入参")
    private Map<String, Object> parameters;

    @ApiModelProperty("错误异常")
    private String exception;

    @ApiModelProperty("记录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @ApiModelProperty("执行器地址")
    private String executorAddr;

    @ApiModelProperty("网关地址")
    private String gatewayAddr;
}
