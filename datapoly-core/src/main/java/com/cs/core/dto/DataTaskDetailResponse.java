// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.dto.ItemParam;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.common.enums.NamingStrategyEnum;
import io.swagger.annotations.*;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("数据任务详情")
public class DataTaskDetailResponse {

    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("任务名称")
    private String name;

    @ApiModelProperty("任务描述")
    private String description;

    @ApiModelProperty("数据源ID")
    private Long datasourceId;

    @ApiModelProperty("SQL语句")
    private String sqlText;

    @ApiModelProperty("入参声明")
    private List<ItemParam> params;

    @ApiModelProperty("结果列命名策略")
    private NamingStrategyEnum namingStrategy;

    @ApiModelProperty("出参类型格式化配置")
    private List<DataTypeFormatMapValue> formatMap;

    @ApiModelProperty("列改名映射")
    private Map<String, String> columnAlias;

    @ApiModelProperty("输出列顺序/子集")
    private List<String> columnOrder;

    @ApiModelProperty("日期/小数等是否按格式化配置转字符串单元格")
    private Boolean applyFormatToString;

    @ApiModelProperty("是否允许${}原生替换")
    private Boolean dollarAllowed;

    @ApiModelProperty("单次投递行数上限")
    private Long maxRows;

    @ApiModelProperty("投递实现标识")
    private String sinkType;

    @ApiModelProperty("投递私有配置JSON")
    private String sinkConfig;

    @ApiModelProperty("是否启用")
    private Boolean enabled;
}
