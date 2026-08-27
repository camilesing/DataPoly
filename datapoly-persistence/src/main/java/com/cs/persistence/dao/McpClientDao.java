// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.persistence.entity.McpClientEntity;
import com.cs.persistence.mapper.McpClientMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class McpClientDao {

    @Resource
    private McpClientMapper mcpClientMapper;

    public void insert(McpClientEntity mcpClientEntity) {
        mcpClientMapper.insert(mcpClientEntity);
    }

    public McpClientEntity getById(Long id) {
        return mcpClientMapper.selectById(id);
    }

    public List<McpClientEntity> listAll(String searchText) {
        return mcpClientMapper.selectList(
                Wrappers.<McpClientEntity>lambdaQuery()
                        .like(StringUtils.isNotBlank(searchText), McpClientEntity::getName, searchText)
                        .orderByDesc(McpClientEntity::getCreateTime)
        );
    }

    public boolean existsAccessToken(String accessToken) {
        QueryWrapper<McpClientEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(McpClientEntity::getToken, accessToken);
        return null != mcpClientMapper.selectOne(queryWrapper);
    }

    public void updateById(McpClientEntity entity) {
        mcpClientMapper.updateById(entity);
    }

    public void deleteById(Long id) {
        mcpClientMapper.deleteById(id);
    }

}
