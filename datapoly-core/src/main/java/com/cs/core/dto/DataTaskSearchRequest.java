// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@Schema(description = "数据任务列表搜索")
public class DataTaskSearchRequest extends EntitySearchRequest {

    @Schema(description = "启用状态过滤（null 表示不过滤）")
    private Boolean enabled;
}
