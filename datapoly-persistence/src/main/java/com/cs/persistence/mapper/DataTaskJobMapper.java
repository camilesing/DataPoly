// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.persistence.entity.DataTaskJobEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface DataTaskJobMapper extends BaseMapper<DataTaskJobEntity> {

    /**
     * Candidate scan for worker claiming, kept deliberately plain: MySQL 5.7 has no
     * FOR UPDATE SKIP LOCKED (8.0+ only), so exclusion between concurrent workers is
     * enforced instead by the conditional PENDING->RUNNING flip in
     * {@code DataTaskJobDao#claimPending}, which returns only the ids this worker
     * actually flipped.
     */
    @Select("SELECT id FROM DATAPOLY_DATA_TASK_JOB "
            + "WHERE status = 'PENDING' ORDER BY id ASC LIMIT #{limit}")
    List<Long> selectClaimableIds(@Param("limit") Integer limit);

    /**
     * Fail running jobs whose lease expired without refresh — a worker crashed or
     * was killed mid-query. NOW() exists with compatible semantics on both platforms.
     */
    @Update("UPDATE DATAPOLY_DATA_TASK_JOB SET status = 'FAILED', error_message = #{message}, finish_time = NOW() "
            + "WHERE status = 'RUNNING' AND lease_expire_at IS NOT NULL AND lease_expire_at < NOW()")
    int failExpiredLeases(@Param("message") String message);
}
