// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "数据任务运行请求")
public class DataTaskRunRequest {

    @Schema(description = "任务定义ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long defId;

    @Schema(description = "入参值（与定义的入参声明同名匹配，嵌套对象用嵌套结构或 parent.sub 键）")
    private Map<String, Object> params;

    @Schema(description = "仅调试用：预览返回的最大行数（默认50，最大200）")
    private Integer previewSize;
}
