// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@ApiModel("统一告警配置")
public class TestAlarmConfigRequest {

    @NotBlank(message = "endpoint不能为空")
    @ApiModelProperty("接口端点")
    private String endpoint;

    @NotBlank(message = "contentType不能为空")
    @ApiModelProperty("入参格式类型")
    private String contentType;

    @NotBlank(message = "inputTemplate不能为空")
    @ApiModelProperty("入参模板")
    private String inputTemplate;

    @NotEmpty(message = "dataModel不能为空")
    @ApiModelProperty("示例数据")
    private List<NameValueBaseResponse> dataModel;
}
