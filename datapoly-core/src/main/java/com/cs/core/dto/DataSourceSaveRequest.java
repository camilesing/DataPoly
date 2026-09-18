// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "数据源保存")
public class DataSourceSaveRequest extends DataSourceBaseRequest {

    @Schema(description = "ID编号")
    private Long id;
}
