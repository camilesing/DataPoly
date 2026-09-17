// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Data
@Builder
@Schema(description = "驱动版本")
public class DatabaseTypeDriverResponse {

    @Schema(description = "驱动版本")
    private String driverVersion;

    @Schema(description = "驱动类名")
    private String driverClass;

    @Schema(description = "版本路径")
    private String driverPath;

    @Schema(description = "驱动JAR")
    private List<String> jarFiles;
}
