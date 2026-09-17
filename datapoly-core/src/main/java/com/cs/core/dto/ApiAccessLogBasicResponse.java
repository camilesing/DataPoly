// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API调用日志记录")
public class ApiAccessLogBasicResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "HTTP状态码")
    private Integer status;

    @Schema(description = "耗时")
    private Long duration;

    @Schema(description = "客户端地址")
    private String ipAddr;

    @Schema(description = "客户端UA")
    private String userAgent;

    @Schema(description = "应用名称")
    private String clientApp;

    @Schema(description = "请求入参")
    private Map<String, Object> parameters;

    @Schema(description = "错误异常")
    private String exception;

    @Schema(description = "记录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "执行器地址")
    private String executorAddr;

    @Schema(description = "网关地址")
    private String gatewayAddr;
}
