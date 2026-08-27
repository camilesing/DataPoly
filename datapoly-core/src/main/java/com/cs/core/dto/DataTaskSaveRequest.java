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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ApiModel("异步数据任务定义")
public class DataTaskSaveRequest {

    @ApiModelProperty("主键（更新时必传）")
    private Long id;

    @ApiModelProperty("任务名称（唯一）")
    private String name;

    @ApiModelProperty("任务描述")
    private String description;

    @ApiModelProperty(value = "数据源ID", required = true)
    private Long datasourceId;

    @ApiModelProperty(value = "SQL语句（支持MyBatis动态标签与#{}占位符）", required = true)
    private String sqlText;

    @ApiModelProperty("入参声明")
    private List<ItemParam> params;

    @ApiModelProperty("结果列命名策略")
    private NamingStrategyEnum namingStrategy;

    @ApiModelProperty("出参类型格式化配置")
    private List<DataTypeFormatMapValue> formatMap;

    @ApiModelProperty("列改名映射：key为命名策略转换后的列名，value为输出别名")
    private Map<String, String> columnAlias;

    @ApiModelProperty("输出列顺序/子集：空表示保留全部原始顺序")
    private List<String> columnOrder;

    @ApiModelProperty("是否将日期/小数等按格式化配置转为字符串单元格")
    private Boolean applyFormatToString;

    @ApiModelProperty("是否允许${}原生替换（默认禁止）")
    private Boolean dollarAllowed;

    @ApiModelProperty("单次投递行数上限，<=0 使用引擎默认上限")
    private Long maxRows;

    @ApiModelProperty(value = "投递实现标识（由扩展方提供）", required = true)
    private String sinkType;

    @ApiModelProperty("投递实现的私有配置JSON（原样透传；请勿存放明文口令）")
    private String sinkConfig;

    @ApiModelProperty("是否启用")
    private Boolean enabled;
}
