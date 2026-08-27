// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.persistence.entity.AppClientEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AppClientMapper extends BaseMapper<AppClientEntity> {

    @Select("<script>"
            + " SELECT * FROM DATAPOLY_APP_CLIENT "
            + " WHERE id in(SELECT client_id FROM DATAPOLY_CLIENT_GROUP WHERE group_id = #{groupId} )"
            + "<if test='searchText != null and searchText.length()>0 '>"
            + " AND name like #{searchText,jdbcType=VARCHAR} "
            + "</if>"
            + " ORDER BY create_time desc "
            + "</script>")
    List<AppClientEntity> searchAppClient(@Param("searchText") String searchText, @Param("groupId") Long groupId);
}
