// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "接口搜索")
public class ApiOnlineSearchRequest extends EntitySearchRequest {

    @Schema(description = "模块ID")
    private List<Long> moduleIds;
}
