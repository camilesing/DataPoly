// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("数据任务列表项")
public class DataTaskBaseResponse {

    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("任务名称")
    private String name;

    @ApiModelProperty("任务描述")
    private String description;

    @ApiModelProperty("数据源ID")
    private Long datasourceId;

    @ApiModelProperty("投递实现标识")
    private String sinkType;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("创建时间")
    private Timestamp createTime;

    @ApiModelProperty("修改时间")
    private Timestamp updateTime;
}
