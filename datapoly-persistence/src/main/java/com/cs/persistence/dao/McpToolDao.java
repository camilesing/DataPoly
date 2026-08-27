// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.persistence.entity.McpToolEntity;
import com.cs.persistence.mapper.McpToolMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class McpToolDao {

    @Resource
    private McpToolMapper mcpToolMapper;


    public void insert(McpToolEntity entity) {
        mcpToolMapper.insert(entity);
    }

    public McpToolEntity getById(Long id) {
        return mcpToolMapper.selectById(id);
    }

    public List<McpToolEntity> listAll(String searchText) {
        return mcpToolMapper.selectList(
                Wrappers.<McpToolEntity>lambdaQuery()
                        .like(StringUtils.isNotBlank(searchText), McpToolEntity::getName, searchText)
                        .orderByDesc(McpToolEntity::getCreateTime)
        );
    }

    public void updateById(McpToolEntity entity) {
        mcpToolMapper.updateById(entity);
    }

    public void deleteById(Long id) {
        mcpToolMapper.deleteById(id);
    }
}
