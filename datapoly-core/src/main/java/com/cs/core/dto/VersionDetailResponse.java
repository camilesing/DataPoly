// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@ApiModel("版本记录详情")
public class VersionDetailResponse extends VersionCommitResponse {

    @ApiModelProperty("版本详情")
    private ApiAssignmentDetailResponse detail;
}
