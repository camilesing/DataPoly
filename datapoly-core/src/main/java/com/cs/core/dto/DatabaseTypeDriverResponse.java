// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.util.List;

@Data
@Builder
@ApiModel("驱动版本")
public class DatabaseTypeDriverResponse {

    @ApiModelProperty("驱动版本")
    private String driverVersion;

    @ApiModelProperty("驱动类名")
    private String driverClass;

    @ApiModelProperty("版本路径")
    private String driverPath;

    @ApiModelProperty("驱动JAR")
    private List<String> jarFiles;
}
