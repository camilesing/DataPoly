// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.common.dto.IdWithName;
import com.cs.persistence.entity.ClientGroupEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ClientGroupMapper extends BaseMapper<ClientGroupEntity> {

    @Select("SELECT g.id,g.name from DATAPOLY_CLIENT_GROUP a LEFT JOIN DATAPOLY_API_GROUP g on a.group_id=g.id where a.client_id= #{id}")
    List<IdWithName> getGroupAuth(@Param("id") Long id);

    @Insert("<script>"
            + "INSERT INTO DATAPOLY_CLIENT_GROUP(client_id,group_id) VALUES "
            + "<foreach collection='entities' item='item' separator=',' > "
            + "  ( #{item.clientId} ,#{item.groupId} ) "
            + "</foreach>"
            + "</script>")
    void insertList(@Param("entities") List<ClientGroupEntity> entities);
}
