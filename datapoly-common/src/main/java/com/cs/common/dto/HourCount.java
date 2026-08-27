// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("按照小时的统计")
public class HourCount implements Serializable {

    @ApiModelProperty("小时(0-23)")
    private Integer hour;

    @ApiModelProperty("总数")
    private Long count;
}
