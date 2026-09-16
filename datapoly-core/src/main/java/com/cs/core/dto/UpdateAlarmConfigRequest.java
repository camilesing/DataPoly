// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.OnOffEnum;
import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.*;

@Data
@NoArgsConstructor
@ApiModel("统一告警配置")
public class UpdateAlarmConfigRequest {

    @NotNull(message = "status不能为null")
    @ApiModelProperty("开启状态")
    private OnOffEnum status;

    @NotBlank(message = "endpoint不能为空")
    @Pattern(regexp = "^https?://\\S+$", message = "endpoint必须是http/https URL")
    @ApiModelProperty("接口端点")
    private String endpoint;

    @NotBlank(message = "contentType不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*/[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*$",
            message = "contentType必须是合法的 type/subtype（不允许携带参数）")
    @ApiModelProperty("入参格式类型")
    private String contentType;

    @NotBlank(message = "inputTemplate不能为空")
    @ApiModelProperty("入参模板")
    private String inputTemplate;
}
