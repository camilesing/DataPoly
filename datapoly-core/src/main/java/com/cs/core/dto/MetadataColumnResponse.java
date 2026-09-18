// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "列信息")
public class MetadataColumnResponse {

    @Schema(description = "列名")
    private String name;

    @Schema(description = "列类型")
    private String type;

    @Schema(description = "注释")
    private String remarks;
}
