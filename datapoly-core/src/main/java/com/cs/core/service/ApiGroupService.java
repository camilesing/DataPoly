// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.exception.*;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.persistence.dao.*;
import com.cs.persistence.entity.ApiGroupEntity;
import com.cs.persistence.util.PageUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class ApiGroupService {

    @Resource
    private ApiGroupDao apiGroupDao;
    @Resource
    private ApiAssignmentDao apiAssignmentDao;
    @Resource
    private AppClientDao appClientDao;

    public void createGroup(String name) {
        try {
            apiGroupDao.insert(ApiGroupEntity.builder().name(name).build());
        } catch (DuplicateKeyException e) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, "group.name.already.exists");
        }
    }

    public void updateGroup(Long id, String newName) {
        ApiGroupEntity apiGroupEntity = apiGroupDao.getById(id);
        apiGroupEntity.setName(newName);
        try {
            apiGroupDao.updateById(apiGroupEntity);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, "group.name.already.exists");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        if (id.equals(1L)) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "group.forbid.delete.default");
        }
        if (apiAssignmentDao.existsGroupById(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, "group.used.by.api");
        }
        if (SpringUtil.getBean(ApiOnlineDao.class).existsGroupById(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, "group.used.by.api");
        }
        apiGroupDao.deleteById(id);
        appClientDao.deleteClientAuthByGroupId(id);
    }

    public PageResult<ApiGroupEntity> listAll(EntitySearchRequest request) {
        return PageUtils.getPage(
                () -> apiGroupDao.listAll(request.getSearchText()),
                request.getPage(),
                request.getSize()
        );
    }
}
