// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedEntityIdName {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "是否选中")
    private Boolean selected;
}
