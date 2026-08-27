// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.persistence.entity.ApiGroupEntity;
import com.cs.persistence.mapper.ApiGroupMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class ApiGroupDao {

    @Resource
    private ApiGroupMapper apiGroupMapper;

    public void insert(ApiGroupEntity entity) {
        apiGroupMapper.insert(entity);
    }

    public ApiGroupEntity getById(Long id) {
        return apiGroupMapper.selectById(id);
    }

    public List<ApiGroupEntity> listAll() {
        return listAll(null);
    }

    public List<ApiGroupEntity> listAll(String searchText) {
        return apiGroupMapper.selectList(
                Wrappers.<ApiGroupEntity>lambdaQuery()
                        .like(StringUtils.hasText(searchText), ApiGroupEntity::getName, searchText)
                        .orderByDesc(ApiGroupEntity::getId)
        );
    }

    public void updateById(ApiGroupEntity entity) {
        apiGroupMapper.updateById(entity);
    }

    public void deleteById(Long id) {
        apiGroupMapper.deleteById(id);
    }
}
