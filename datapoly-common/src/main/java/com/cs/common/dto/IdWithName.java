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
@Schema(description = "ID名称")
public class IdWithName implements Serializable {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "名称")
    private String name;
}
