// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.common.enums.OnOffEnum;
import com.cs.persistence.entity.UnifyAlarmEntity;
import com.cs.persistence.mapper.UnifyAlarmMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class UnifyAlarmDao {

    private static final Long ID = 1L;

    @Resource
    private UnifyAlarmMapper unifyAlarmMapper;

    public UnifyAlarmEntity getUnifyAlarmConfig() {
        return unifyAlarmMapper.selectById(ID);
    }

    public void turnOn() {
        unifyAlarmMapper.updateStatus(ID, OnOffEnum.ON);
    }

    public void turnOff() {
        unifyAlarmMapper.updateStatus(ID, OnOffEnum.OFF);
    }

    public void update(OnOffEnum status, String endpoint, String contentType, String inputTemplate) {
        unifyAlarmMapper.updateById(
                UnifyAlarmEntity.builder()
                        .id(ID)
                        .status(status)
                        .endpoint(endpoint)
                        .contentType(contentType)
                        .inputTemplate(inputTemplate)
                        .build());
    }
}
