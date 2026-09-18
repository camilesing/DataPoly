// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "统一告警配置")
public class TestAlarmConfigRequest {

    @NotBlank(message = "endpoint不能为空")
    @Pattern(regexp = "^https?://\\S+$", message = "endpoint必须是http/https URL")
    @Schema(description = "接口端点")
    private String endpoint;

    @NotBlank(message = "contentType不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*/[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*$",
            message = "contentType必须是合法的 type/subtype（不允许携带参数）")
    @Schema(description = "入参格式类型")
    private String contentType;

    @NotBlank(message = "inputTemplate不能为空")
    @Schema(description = "入参模板")
    private String inputTemplate;

    @NotEmpty(message = "dataModel不能为空")
    @Schema(description = "示例数据")
    private List<NameValueBaseResponse> dataModel;
}
