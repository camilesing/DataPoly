// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.mapper.DataTaskDefMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class DataTaskDefDao {

    @Resource
    private DataTaskDefMapper dataTaskDefMapper;

    public DataTaskDefEntity getById(Long id) {
        return dataTaskDefMapper.selectById(id);
    }

    public DataTaskDefEntity getByName(String name) {
        QueryWrapper<DataTaskDefEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DataTaskDefEntity::getName, name);
        return dataTaskDefMapper.selectOne(queryWrapper);
    }

    public void insert(DataTaskDefEntity entity) {
        dataTaskDefMapper.insert(entity);
    }

    public void update(DataTaskDefEntity entity) {
        dataTaskDefMapper.updateById(entity);
    }

    public void deleteById(Long id) {
        dataTaskDefMapper.deleteById(id);
    }

    public List<DataTaskDefEntity> searchAll(String searchText) {
        QueryWrapper<DataTaskDefEntity> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(searchText)) {
            String like = "%" + searchText + "%";
            queryWrapper.lambda()
                    .like(DataTaskDefEntity::getName, like)
                    .or()
                    .like(DataTaskDefEntity::getDescription, like);
        }
        queryWrapper.lambda().orderByDesc(DataTaskDefEntity::getId);
        return dataTaskDefMapper.selectList(queryWrapper);
    }

    public boolean existsDatasourceById(Long datasourceId) {
        QueryWrapper<DataTaskDefEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DataTaskDefEntity::getDatasourceId, datasourceId);
        return dataTaskDefMapper.selectCount(queryWrapper) > 0;
    }
}
