// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.common.enums.DataTaskStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.EnumTypeHandler;

import java.sql.Timestamp;

/**
 * One execution record of a data task definition. Workers claim PENDING rows
 * atomically, execute against the snapshot stored at submit time (so later edits of
 * the definition never mutate queued work), refresh total_rows/lease during the run
 * and finalize with SUCCESS/FAILED/CANCELED plus whatever artifact descriptor the
 * delivery provider returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_DATA_TASK_JOB", autoResultMap = true)
public class DataTaskJobEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("def_id")
    private Long defId;

    @TableField("def_name")
    private String defName;

    @TableField(value = "status", typeHandler = EnumTypeHandler.class)
    private DataTaskStatus status;

    /** JSON snapshot of the definition content used by this execution */
    @TableField("snapshot")
    private String snapshot;

    /** JSON of the resolved (nested) parameter values bound for this execution */
    @TableField("params_json")
    private String paramsJson;

    @TableField("cancel_requested")
    private Boolean cancelRequested;

    @TableField("total_rows")
    private Long totalRows;

    @TableField("artifact_uri")
    private String artifactUri;

    /** JSON map returned by the delivery provider (SinkOutcome#info) */
    @TableField("artifact_info")
    private String artifactInfo;

    @TableField("error_message")
    private String errorMessage;

    @TableField("worker_addr")
    private String workerAddr;

    @TableField("submitted_by")
    private String submittedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private Timestamp startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("finish_time")
    private Timestamp finishTime;

    /** Running jobs must be refreshed before this moment or get reaped as lost */
    @TableField("lease_expire_at")
    private Timestamp leaseExpireAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp updateTime;
}
