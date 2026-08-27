// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.persistence.entity.VersionCommitEntity;
import org.apache.ibatis.annotations.*;

public interface VersionCommitMapper extends BaseMapper<VersionCommitEntity> {

    @Select("SELECT max(version) FROM DATAPOLY_VERSION_COMMIT WHERE biz_id = #{bizId} ")
    Integer getMaxVersion(@Param("bizId") Long bizId);

    @Select("SELECT * FROM DATAPOLY_VERSION_COMMIT WHERE biz_id = #{bizId} ORDER BY version DESC LIMIT 1 ")
    VersionCommitEntity getLatestVersion(@Param("bizId") Long bizId);
}
