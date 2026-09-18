// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.dto.ParamValue;
import com.cs.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "SQL调试执行")
public class ApiDebugExecuteRequest {

    @NotNull(message = "datasourceId不能为null")
    @Schema(description = "数据源的ID")
    private Long dataSourceId;

    @NotNull(message = "engine不能为null")
    @Schema(description = "执行引擎:SQL, SCRIPT")
    private ExecuteEngineEnum engine;

    @Schema(description = "数据类型转换格式")
    private List<DataTypeFormatMapValue> formatMap;

    @NotNull(message = "namingStrategy不能为null")
    @Schema(description = "属性命名策略")
    private NamingStrategyEnum namingStrategy;

    @NotEmpty(message = "contextList不能为空")
    @Schema(description = "SQL列表")
    private List<String> contextList;

    @Schema(description = "接口入参列表")
    private List<ParamValue> paramValues;
}
