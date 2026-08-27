// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.common.enums.OnOffEnum;
import com.cs.persistence.entity.UnifyAlarmEntity;
import org.apache.ibatis.annotations.*;

public interface UnifyAlarmMapper extends BaseMapper<UnifyAlarmEntity> {

    @Update("update DATAPOLY_UNIFY_ALARM set status = #{status}　where id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") OnOffEnum status);
}
