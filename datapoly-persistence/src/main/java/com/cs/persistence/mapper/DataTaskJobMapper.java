// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.persistence.entity.DataTaskJobEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface DataTaskJobMapper extends BaseMapper<DataTaskJobEntity> {

    /**
     * Atomic candidate scan for worker claiming. Called inside the claim transaction
     * so selected rows are locked (SKIP LOCKED keeps concurrent workers off each
     * other's candidates); the UPDATE to RUNNING happens on these ids before commit.
     * LIMIT-before-FOR-UPDATE order is valid on both MySQL 8 and PostgreSQL 9.5+.
     */
    @Select("SELECT id FROM DATAPOLY_DATA_TASK_JOB "
            + "WHERE status = 'PENDING' ORDER BY id ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<Long> selectClaimableIds(@Param("limit") Integer limit);

    /**
     * Fail running jobs whose lease expired without refresh — a worker crashed or
     * was killed mid-query. NOW() exists with compatible semantics on both platforms.
     */
    @Update("UPDATE DATAPOLY_DATA_TASK_JOB SET status = 'FAILED', error_message = #{message}, finish_time = NOW() "
            + "WHERE status = 'RUNNING' AND lease_expire_at IS NOT NULL AND lease_expire_at < NOW()")
    int failExpiredLeases(@Param("message") String message);
}
