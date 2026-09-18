// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "统计数量")
public class NameCount implements Serializable {

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数量")
    private Long count;
}
