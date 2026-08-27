// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.common.dto.ItemParam;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.persistence.handler.FormatMapHandler;
import com.cs.persistence.handler.ListParamHandler;
import com.cs.persistence.handler.StringListHandler;
import com.cs.persistence.handler.StringMapHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.EnumTypeHandler;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Reusable configuration of one asynchronous data task: a single SQL statement plus
 * input parameter declarations, output reshaping rules and the delivery target.
 * Delivery providers live outside this codebase (see com.cs.common.datatask SPI);
 * sinkConfig stays opaque and is forwarded verbatim.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_DATA_TASK_DEF", autoResultMap = true)
public class DataTaskDefEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("datasource_id")
    private Long datasourceId;

    /** Single SQL statement, may contain MyBatis dynamic tags and #{} placeholders */
    @TableField("sql_text")
    private String sqlText;

    @TableField(value = "params", typeHandler = ListParamHandler.class)
    private List<ItemParam> params;

    @TableField(value = "naming_strategy", typeHandler = EnumTypeHandler.class)
    private NamingStrategyEnum namingStrategy;

    @TableField(value = "response_format", typeHandler = FormatMapHandler.class)
    private Map<DataTypeFormatEnum, String> responseFormat;

    /** Renaming of result columns; keys match labels AFTER naming-strategy conversion */
    @TableField(value = "column_alias", typeHandler = StringMapHandler.class)
    private Map<String, String> columnAlias;

    /**
     * Output order / subset of result columns; empty means keep every column in its
     * natural order. Entries not matching any column are ignored, unlisted columns
     * are dropped when this list is present.
     */
    @TableField(value = "column_order", typeHandler = StringListHandler.class)
    private List<String> columnOrder;

    /** Convert date/time/decimal cells to strings following responseFormat patterns */
    @TableField("apply_format_to_string")
    private Boolean applyFormatToString;

    /** Allow ${} raw substitution in the SQL (task authors are trusted operators) */
    @TableField("dollar_allowed")
    private Boolean dollarAllowed;

    /** Hard cap of delivered rows; <=0 falls back to the engine default */
    @TableField("max_rows")
    private Long maxRows;

    /** Registration key of the delivery provider built through the datatask SPI */
    @TableField("sink_type")
    private String sinkType;

    /** Opaque JSON consumed by the delivery provider */
    @TableField("sink_config")
    private String sinkConfig;

    @TableField("enabled")
    private Boolean enabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp updateTime;
}
