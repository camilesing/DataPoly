// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.dto.ParamValue;
import com.cs.common.enums.*;
import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@ApiModel("SQL调试执行")
public class ApiDebugExecuteRequest {

    @NotNull(message = "datasourceId不能为null")
    @ApiModelProperty("数据源的ID")
    private Long dataSourceId;

    @NotNull(message = "engine不能为null")
    @ApiModelProperty("执行引擎:SQL, SCRIPT")
    private ExecuteEngineEnum engine;

    @ApiModelProperty("数据类型转换格式")
    private List<DataTypeFormatMapValue> formatMap;

    @NotNull(message = "namingStrategy不能为null")
    @ApiModelProperty("属性命名策略")
    private NamingStrategyEnum namingStrategy;

    @NotEmpty(message = "contextList不能为空")
    @ApiModelProperty("SQL列表")
    private List<String> contextList;

    @ApiModelProperty("接口入参列表")
    private List<ParamValue> paramValues;
}
