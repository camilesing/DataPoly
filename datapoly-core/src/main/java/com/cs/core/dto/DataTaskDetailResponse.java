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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据任务详情")
public class DataTaskDetailResponse {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "数据源ID")
    private Long datasourceId;

    @Schema(description = "SQL语句")
    private String sqlText;

    @Schema(description = "入参声明")
    private List<ItemParam> params;

    @Schema(description = "结果列命名策略")
    private NamingStrategyEnum namingStrategy;

    @Schema(description = "出参类型格式化配置")
    private List<DataTypeFormatMapValue> formatMap;

    @Schema(description = "列改名映射")
    private Map<String, String> columnAlias;

    @Schema(description = "输出列顺序/子集")
    private List<String> columnOrder;

    @Schema(description = "日期/小数等是否按格式化配置转字符串单元格")
    private Boolean applyFormatToString;

    @Schema(description = "是否允许${}原生替换")
    private Boolean dollarAllowed;

    @Schema(description = "单次投递行数上限")
    private Long maxRows;

    @Schema(description = "投递实现标识")
    private String sinkType;

    @Schema(description = "投递私有配置JSON")
    private String sinkConfig;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
