// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据任务列表项")
public class DataTaskBaseResponse {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "数据源ID")
    private Long datasourceId;

    @Schema(description = "投递实现标识")
    private String sinkType;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private Timestamp createTime;

    @Schema(description = "修改时间")
    private Timestamp updateTime;
}
