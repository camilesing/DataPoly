// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "版本记录详情")
public class VersionDetailResponse extends VersionCommitResponse {

    @Schema(description = "版本详情")
    private ApiAssignmentDetailResponse detail;
}
