// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@NoArgsConstructor
@Data
@Schema(description = "数据库类型")
public class DatabaseTypeDetailResponse {

    @Schema(description = "编号")
    private Integer id;

    @Schema(description = "数据库类型")
    private String type;

    @Schema(description = "驱动类")
    private String driver;

    @Schema(description = "连接串样例")
    private String sample;
}
