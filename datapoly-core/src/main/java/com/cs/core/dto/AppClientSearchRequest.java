// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "客户端应用列表搜索")
public class AppClientSearchRequest extends EntitySearchRequest {

    @Schema(description = "分组ID")
    private Long groupId;
}
