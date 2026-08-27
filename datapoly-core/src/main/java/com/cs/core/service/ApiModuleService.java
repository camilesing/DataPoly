// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.exception.*;
import com.cs.core.dto.*;
import com.cs.persistence.dao.*;
import com.cs.persistence.entity.*;
import com.cs.persistence.util.PageUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApiModuleService {

    @Resource
    private ApiModuleDao apiModuleDao;
    @Resource
    private ApiAssignmentDao apiAssignmentDao;

    public void createModule(String name) {
        try {
            apiModuleDao.insert(ApiModuleEntity.builder().name(name).build());
        } catch (DuplicateKeyException e) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, "module.name.already.exists");
        }
    }

    public void updateModule(Long id, String newName) {
        ApiModuleEntity moduleEntity = apiModuleDao.getById(id);
        moduleEntity.setName(newName);
        try {
            apiModuleDao.updateById(moduleEntity);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, "module.name.already.exists");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(Long id) {
        if (apiAssignmentDao.existsModuleById(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, "module.used.by.api");
        }
        if (SpringUtil.getBean(ApiOnlineDao.class).existsModuleById(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, "module.used.by.api");
        }
        apiModuleDao.deleteById(id);
    }

    public PageResult<ApiModuleEntity> listAll(EntitySearchRequest request) {
        return PageUtils.getPage(
                () -> apiModuleDao.listAll(request.getSearchText()),
                request.getPage(),
                request.getSize()
        );
    }

    public List<ApiModuleAssignments> moduleTree(Long groupId) {
        Map<Long, List<ModuleAssignmentEntity>> moduleIdListMap = apiAssignmentDao.getModuleAssignments()
                .stream().collect(Collectors.groupingBy(ModuleAssignmentEntity::getModuleId));
        List<ApiModuleAssignments> results = new ArrayList<>(moduleIdListMap.size());
        for (Map.Entry<Long, List<ModuleAssignmentEntity>> entry : moduleIdListMap.entrySet()) {
            ModuleAssignmentEntity first = entry.getValue().get(0);
            ApiModuleAssignments module = new ApiModuleAssignments();
            module.setId(first.getModuleId());
            module.setName(first.getModuleName());
            module.setChildren(
                    entry.getValue().stream()
                            .map(one ->
                                    SelectedEntityIdName.builder()
                                            .id(one.getAssigmentId())
                                            .name(String.format("[%d]%s", one.getAssigmentId(), one.getAssigmentName()))
                                            .selected(one.getGroupId().equals(groupId))
                                            .build())
                            .collect(Collectors.toList()));
            results.add(module);
        }
        return results;
    }
}
