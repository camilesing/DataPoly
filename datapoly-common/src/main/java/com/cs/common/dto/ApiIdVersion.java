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
@Schema(description = "API的ID及版本号")
public class ApiIdVersion implements Serializable {

    @Schema(description = "API的ID")
    private Long apiId;

    @Schema(description = "commitId")
    private Long commitId;

    @Schema(description = "版本号")
    private Integer version;
}
