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
@ApiModel("按照日期的统计")
public class DateCount implements Serializable {

    @ApiModelProperty("日期")
    private String ofDate;

    @ApiModelProperty("总数")
    private Long total;

    @ApiModelProperty("成功数")
    private Long success;
}
