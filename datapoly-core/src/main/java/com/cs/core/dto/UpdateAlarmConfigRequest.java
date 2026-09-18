// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.OnOffEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@Schema(description = "统一告警配置")
public class UpdateAlarmConfigRequest {

    @NotNull(message = "status不能为null")
    @Schema(description = "开启状态")
    private OnOffEnum status;

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
}
