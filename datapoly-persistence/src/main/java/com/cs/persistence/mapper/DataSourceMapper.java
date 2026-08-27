// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.common.dto.NameCount;
import com.cs.persistence.entity.DataSourceEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DataSourceMapper extends BaseMapper<DataSourceEntity> {

    @Select("SELECT type as name,count(1) as count from DATAPOLY_DATASOURCE GROUP BY type ORDER BY count DESC")
    List<NameCount> getDatasourceTypeRatio();
}
