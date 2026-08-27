// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.common.enums.*;
import com.cs.persistence.entity.FirewallRulesEntity;
import com.cs.persistence.mapper.FirewallRulesMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class FirewallRulesDao {

    private static final Long ID = 1L;

    @Resource
    private FirewallRulesMapper firewallRulesMapper;

    public FirewallRulesEntity getFirewallRules() {
        return firewallRulesMapper.selectById(ID);
    }

    public void turnOn() {
        firewallRulesMapper.updateStatus(ID, OnOffEnum.ON);
    }

    public void turnOff() {
        firewallRulesMapper.updateStatus(ID, OnOffEnum.OFF);
    }

    public void update(OnOffEnum status, WhiteBlackEnum mode, String addresses) {
        firewallRulesMapper.updateById(
                FirewallRulesEntity.builder()
                        .id(ID)
                        .status(status)
                        .mode(mode)
                        .addresses(addresses)
                        .build());
    }
}
