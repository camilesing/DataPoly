// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("数据任务执行记录")
public class DataTaskJobView {

    @ApiModelProperty("执行记录ID")
    private Long id;

    @ApiModelProperty("任务定义ID")
    private Long defId;

    @ApiModelProperty("任务名称")
    private String defName;

    @ApiModelProperty("执行状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED")
    private String status;

    @ApiModelProperty("已处理行数")
    private Long totalRows;

    @ApiModelProperty("取消标记（RUNNING阶段的取消请求）")
    private Boolean cancelRequested;

    @ApiModelProperty("产物引用地址（由投递实现返回）")
    private String artifactUri;

    @ApiModelProperty("产物附加信息")
    private Map<String, Object> artifactInfo;

    @ApiModelProperty("失败原因")
    private String errorMessage;

    @ApiModelProperty("执行的worker地址")
    private String workerAddr;

    @ApiModelProperty("提交人")
    private String submittedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty("开始时间")
    private Timestamp startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty("结束时间")
    private Timestamp finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty("创建时间")
    private Timestamp createTime;
}
