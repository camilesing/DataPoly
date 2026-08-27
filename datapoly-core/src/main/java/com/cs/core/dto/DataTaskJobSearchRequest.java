// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.DataTaskStatus;
import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@ApiModel("数据任务执行记录搜索")
public class DataTaskJobSearchRequest extends EntitySearchRequest {

    @ApiModelProperty("任务定义ID")
    private Long defId;

    @ApiModelProperty("执行状态")
    private DataTaskStatus status;
}
