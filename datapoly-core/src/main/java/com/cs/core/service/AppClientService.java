// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.cs.cache.CacheFactory;
import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.common.util.TokenUtils;
import com.cs.core.dto.*;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.AppClientEntity;
import com.cs.persistence.util.PageUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AppClientService {

    @Resource
    private AppClientDao appClientDao;
    @Resource
    private CacheFactory cacheFactory;

    public void create(AppClientSaveRequest request) {
        if (null != appClientDao.getByAppKey(request.getAppKey())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "Duplicate app key :" + request.getAppKey());
        }
        if (appClientDao.getByName(request.getName()).size() > 0) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "Duplicate Name :" + request.getName());
        }

        AppClientEntity appClientEntity = new AppClientEntity();
        BeanUtil.copyProperties(request, appClientEntity);
        appClientEntity.setAppSecret(TokenUtils.generateValue());
        appClientEntity.setExpireDuration(request.getExpireTime().getDuration());
        if (DurationTimeEnum.TIME_VALUE.equals(request.getExpireTime().getDuration())) {
            appClientEntity.setExpireAt(request.getExpireTime().getValue() + (System.currentTimeMillis() / 1000));
        } else if (DurationTimeEnum.FOR_EVER.equals(request.getExpireTime().getDuration())) {
            appClientEntity.setExpireAt(-1L);
        } else {
            appClientEntity.setExpireAt(0L);
        }
        appClientEntity.setAccessToken(null);
        appClientEntity.setTokenAlive(request.getTokenAlive());
        appClientDao.insert(appClientEntity);
    }

    public void delete(Long id) {
        AppClientEntity entity = appClientDao.getById(id);
        if (null != entity) {
            String token = entity.getAccessToken();
            if (StringUtils.isNotBlank(token)) {
                Map<String, AccessToken> tokenClientMap = cacheFactory
                        .getCacheMap(Constants.CACHE_KEY_TOKEN_CLIENT, AccessToken.class);
                tokenClientMap.remove(token);
            }
            appClientDao.deleteById(id);
        }
    }

    public PageResult<AppClientDetailResponse> searchList(AppClientSearchRequest request) {
        Supplier<List<AppClientDetailResponse>> method = () -> {
            List<AppClientEntity> list = appClientDao.listAll(request.getSearchText(), request.getGroupId());
            return list.stream().map(appClientEntity -> {
                AppClientDetailResponse response = new AppClientDetailResponse();
                BeanUtil.copyProperties(appClientEntity, response);
                ExpireTimeEnum expireTime = ExpireTimeEnum
                        .from(appClientEntity.getExpireDuration(), appClientEntity.getExpireAt());
                Boolean isExpired = (System.currentTimeMillis() / 1000) > response.getExpireAt();
                if (DurationTimeEnum.TIME_VALUE != appClientEntity.getExpireDuration()) {
                    isExpired = false;
                }
                response.setExpireType(expireTime.getDescription());
                response.setIsExpired(isExpired);
                if (response.getExpireAt() > 0) {
                    long expireAt = response.getExpireAt();
                    response.setExpireAtStr(DateUtil.format(new Date(expireAt * 1000), "yyyy-MM-dd HH:mm:ss"));
                }
                return response;
            }).collect(Collectors.toList());
        };

        return PageUtils.getPage(method, request.getPage(), request.getSize());
    }

    public String getSecret(Long id) {
        AppClientEntity entity = appClientDao.getById(id);
        if (null == entity) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "common.id.not.found", id);
        }
        return entity.getAppSecret();
    }

    public void createGroupAuth(AppClientGroupRequest request) {
        appClientDao.saveAuthGroup(request.getId(), request.getGroupIds());
    }

    public List<EntityIdNameResponse> getGroupAuth(Long id) {
        return appClientDao.getGroupAuth(id).stream()
                .map(item -> new EntityIdNameResponse(item.getId(), item.getName()))
                .collect(Collectors.toList());
    }

}
