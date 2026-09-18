// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "列表搜索")
public class EntitySearchRequest {

    @Schema(description = "页号")
    private Integer page;

    @Schema(description = "页大小")
    private Integer size;

    @Schema(description = "关键词")
    private String searchText;

}
