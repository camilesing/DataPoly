// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.common.enums.OnOffEnum;
import com.cs.persistence.entity.FirewallRulesEntity;
import org.apache.ibatis.annotations.*;

public interface FirewallRulesMapper extends BaseMapper<FirewallRulesEntity> {

    @Update("update DATAPOLY_FIREWALL_RULES set status = #{status}　where id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") OnOffEnum status);

}
