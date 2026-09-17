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
@Schema(description = "按照小时的统计")
public class HourCount implements Serializable {

    @Schema(description = "小时(0-23)")
    private Integer hour;

    @Schema(description = "总数")
    private Long count;
}
