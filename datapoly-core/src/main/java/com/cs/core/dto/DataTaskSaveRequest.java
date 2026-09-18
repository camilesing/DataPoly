// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.dto.ItemParam;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.common.enums.NamingStrategyEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "异步数据任务定义")
public class DataTaskSaveRequest {

    @Schema(description = "主键（更新时必传）")
    private Long id;

    @Schema(description = "任务名称（唯一）")
    private String name;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "数据源ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasourceId;

    @Schema(description = "SQL语句（支持MyBatis动态标签与#{}占位符）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sqlText;

    @Schema(description = "入参声明")
    private List<ItemParam> params;

    @Schema(description = "结果列命名策略")
    private NamingStrategyEnum namingStrategy;

    @Schema(description = "出参类型格式化配置")
    private List<DataTypeFormatMapValue> formatMap;

    @Schema(description = "列改名映射：key为命名策略转换后的列名，value为输出别名")
    private Map<String, String> columnAlias;

    @Schema(description = "输出列顺序/子集：空表示保留全部原始顺序")
    private List<String> columnOrder;

    @Schema(description = "是否将日期/小数等按格式化配置转为字符串单元格")
    private Boolean applyFormatToString;

    @Schema(description = "是否允许${}原生替换（默认禁止）")
    private Boolean dollarAllowed;

    @Schema(description = "单次投递行数上限，<=0 使用引擎默认上限")
    private Long maxRows;

    @Schema(description = "投递实现标识（由扩展方提供）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sinkType;

    @Schema(description = "投递实现的私有配置JSON（原样透传；请勿存放明文口令）")
    private String sinkConfig;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
