// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@ApiModel("数据源保存")
public class DataSourceSaveRequest extends DataSourceBaseRequest {

    @ApiModelProperty("ID编号")
    private Long id;
}
