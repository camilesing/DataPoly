// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@Schema(description = "版本发布请求")
public class AssignmentPublishRequest {

    @NotNull(message = "id不能为null")
    @Schema(description = "接口ID")
    private Long id;

    @Schema(description = "版本描述")
    private String description;
}
