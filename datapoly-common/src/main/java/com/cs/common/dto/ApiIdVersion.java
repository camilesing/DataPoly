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
@ApiModel("API的ID及版本号")
public class ApiIdVersion implements Serializable {

    @ApiModelProperty("API的ID")
    private Long apiId;

    @ApiModelProperty("commitId")
    private Long commitId;

    @ApiModelProperty("版本号")
    private Integer version;
}
