// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.*;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class SystemParamService {

    @Resource
    private SystemParamDao systemParamDao;

    public Object getByParamKey(String key) {
        SystemParamEntity entity = systemParamDao.getByParamKey(key);
        if (null == entity) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "common.no.key", key);
        }
        Class clazz = entity.getParamType().getClazz();
        String paramValue = entity.getParamValue();
        return clazz.cast(entity.getParamType().getConverter().apply(paramValue));
    }

    public void updateByParamKey(String key, String value) {
        SystemParamEntity entity = systemParamDao.getByParamKey(key);
        if (null == entity) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "common.no.key", key);
        }
        Class clazz = entity.getParamType().getClazz();
        Object paramValue = clazz.cast(entity.getParamType().getConverter().apply(value));
        if (null == paramValue) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "common.invalid.param.value", value);
        }
        systemParamDao.updateByParamKey(key, String.valueOf(paramValue));
    }

    public Integer getIntByParamKey(String key, int defaultValue) {
        SystemParamEntity entity = systemParamDao.getByParamKey(key);
        if (null == entity) {
            return defaultValue;
        }
        if (ParamTypeEnum.LONG.equals(entity.getParamType())) {
            try {
                return Integer.parseInt(entity.getParamValue());
            } catch (Exception e) {
                log.warn("Read system param integer value by key={} failed,use default value={},error:{} ",
                        key, defaultValue, e.getMessage());
            }
        }
        return defaultValue;
    }
}
