// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.DataTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "数据任务执行记录搜索")
public class DataTaskJobSearchRequest extends EntitySearchRequest {

    @Schema(description = "任务定义ID")
    private Long defId;

    @Schema(description = "执行状态")
    private DataTaskStatus status;
}
