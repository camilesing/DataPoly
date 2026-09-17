// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "列表搜索")
public class AssignmentSearchRequest extends EntitySearchRequest {

    @Schema(description = "是否上线")
    private Boolean online;

    @Schema(description = "分组ID")
    private Long groupId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "是否公开")
    private Boolean open;
}
