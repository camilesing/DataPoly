// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据任务执行记录")
public class DataTaskJobView {

    @Schema(description = "执行记录ID")
    private Long id;

    @Schema(description = "任务定义ID")
    private Long defId;

    @Schema(description = "任务名称")
    private String defName;

    @Schema(description = "执行状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED")
    private String status;

    @Schema(description = "已处理行数")
    private Long totalRows;

    @Schema(description = "取消标记（RUNNING阶段的取消请求）")
    private Boolean cancelRequested;

    @Schema(description = "产物引用地址（由投递实现返回）")
    private String artifactUri;

    @Schema(description = "产物附加信息")
    private Map<String, Object> artifactInfo;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "执行的worker地址")
    private String workerAddr;

    @Schema(description = "提交人")
    private String submittedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Timestamp startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private Timestamp finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Timestamp createTime;
}
