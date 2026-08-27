// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.mapper.DataSourceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DataSourceDao {

    @Resource
    private DataSourceMapper dataSourceMapper;

    public void insert(DataSourceEntity dataSourceEntity) {
        dataSourceMapper.insert(dataSourceEntity);
    }

    public DataSourceEntity getById(Long id) {
        return dataSourceMapper.selectById(id);
    }

    public DataSourceEntity getByName(String name) {
        QueryWrapper<DataSourceEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DataSourceEntity::getName, name);
        return dataSourceMapper.selectOne(queryWrapper);
    }

    public List<DataSourceEntity> listAll(String searchText) {
        return dataSourceMapper.selectList(
                Wrappers.<DataSourceEntity>lambdaQuery()
                        .like(StringUtils.hasText(searchText), DataSourceEntity::getName, searchText)
                        .orderByDesc(DataSourceEntity::getCreateTime)
        );
    }

    public List<DataSourceEntity> listAll() {
        return listAll(null);
    }

    public Set<Long> getAllIdList() {
        LambdaQueryWrapper<DataSourceEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(DataSourceEntity::getId);
        return dataSourceMapper.selectList(queryWrapper).stream().map(DataSourceEntity::getId)
                .collect(Collectors.toSet());
    }

    public void updateById(DataSourceEntity databaseConnectionEntity) {
        dataSourceMapper.updateById(databaseConnectionEntity);
    }

    public void deleteById(Long id) {
        dataSourceMapper.deleteById(id);
    }

    public int getTotalCount() {
        return dataSourceMapper.selectCount(null).intValue();
    }
}
