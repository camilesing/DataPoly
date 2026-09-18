// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "客户端分组关联")
public class AppClientGroupRequest {

    @NotNull(message = "id不能为null")
    @Schema(description = "客户端应用ID")
    private Long id;

    @Schema(description = "分组ID列表")
    private List<Long> groupIds;
}
