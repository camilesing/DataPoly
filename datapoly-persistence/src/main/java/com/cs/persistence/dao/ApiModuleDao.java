// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.persistence.entity.ApiModuleEntity;
import com.cs.persistence.mapper.ApiModuleMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class ApiModuleDao {

    @Resource
    private ApiModuleMapper apiModuleMapper;

    public void insert(ApiModuleEntity entity) {
        apiModuleMapper.insert(entity);
    }

    public ApiModuleEntity getById(Long id) {
        return apiModuleMapper.selectById(id);
    }

    public List<ApiModuleEntity> listAll() {
        return listAll(null);
    }

    public List<ApiModuleEntity> listAll(String searchText) {
        return apiModuleMapper.selectList(
                Wrappers.<ApiModuleEntity>lambdaQuery()
                        .like(StringUtils.hasText(searchText), ApiModuleEntity::getName, searchText)
                        .orderByDesc(ApiModuleEntity::getId)
        );
    }

    public void updateById(ApiModuleEntity entity) {
        apiModuleMapper.updateById(entity);
    }

    public void deleteById(Long id) {
        apiModuleMapper.deleteById(id);
    }
}
