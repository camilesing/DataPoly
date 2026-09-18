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
@Schema(description = "按照日期的统计")
public class DateCount implements Serializable {

    @Schema(description = "日期")
    private String ofDate;

    @Schema(description = "总数")
    private Long total;

    @Schema(description = "成功数")
    private Long success;
}
