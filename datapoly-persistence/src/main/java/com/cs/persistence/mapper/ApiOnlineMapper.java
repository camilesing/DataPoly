// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.common.dto.ApiIdVersion;
import com.cs.persistence.entity.ApiOnlineEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ApiOnlineMapper extends BaseMapper<ApiOnlineEntity> {

    @Insert("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + " INSERT INTO DATAPOLY_API_ONLINE "
            + " (name, method, path, api_id, group_id, module_id, datasource_id, open, alarm, flow_status, commit_id, version, content) "
            + " VALUES (#{name}, #{method,typeHandler=org.apache.ibatis.type.EnumTypeHandler}, #{path}, #{apiId}, "
            + " #{groupId}, #{moduleId}, #{datasourceId}, #{open}, #{alarm}, #{flowStatus}, #{commitId}, #{version}, #{content}) "
            + " ON DUPLICATE KEY UPDATE "
            + " name = VALUES(name), api_id = VALUES(api_id), group_id = VALUES(group_id), module_id = VALUES(module_id), "
            + " datasource_id = VALUES(datasource_id), open = VALUES(open), alarm = VALUES(alarm), "
            + " flow_status = VALUES(flow_status), commit_id = VALUES(commit_id), version = VALUES(version), "
            + " content = VALUES(content), update_time = CURRENT_TIMESTAMP "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + " INSERT INTO DATAPOLY_API_ONLINE "
            + " (name, method, path, api_id, group_id, module_id, datasource_id, open, alarm, flow_status, commit_id, version, content) "
            + " VALUES (#{name}, #{method,typeHandler=org.apache.ibatis.type.EnumTypeHandler}, #{path}, #{apiId}, "
            + " #{groupId}, #{moduleId}, #{datasourceId}, #{open}, #{alarm}, #{flowStatus}, #{commitId}, #{version}, #{content}) "
            + " ON CONFLICT (\"method\", \"path\") DO UPDATE SET "
            + " name = EXCLUDED.name, api_id = EXCLUDED.api_id, group_id = EXCLUDED.group_id, module_id = EXCLUDED.module_id, "
            + " datasource_id = EXCLUDED.datasource_id, open = EXCLUDED.open, alarm = EXCLUDED.alarm, "
            + " flow_status = EXCLUDED.flow_status, commit_id = EXCLUDED.commit_id, version = EXCLUDED.version, "
            + " content = EXCLUDED.content, update_time = CURRENT_TIMESTAMP "
            + "</if>"
            + "</script>")
    int upsert(ApiOnlineEntity entity);

    @Select("<script>"
            + " SELECT api_id, commit_id, version FROM DATAPOLY_API_ONLINE "
            + " WHERE api_id IN "
            + "<foreach collection='apiIds' item='item' open='(' separator=',' close=')'> "
            + "   #{item} "
            + "</foreach>"
            + "</script>")
    List<ApiIdVersion> filterOnline(@Param("apiIds") List<Long> apiIds);

    @Update("UPDATE DATAPOLY_API_ONLINE SET group_id = 1 WHERE group_id=#{groupId}")
    void resetGroup(@Param("groupId") Long groupId);

    @Update("<script>"
            + "UPDATE DATAPOLY_API_ONLINE SET group_id = #{groupId} WHERE api_id IN "
            + "<foreach collection='apiIds' item='item' open='(' separator=',' close=')'> "
            + "   #{item} "
            + "</foreach>"
            + "</script>")
    void updateGroup(@Param("groupId") Long groupId, @Param("apiIds") List<Long> apiIds);
}
