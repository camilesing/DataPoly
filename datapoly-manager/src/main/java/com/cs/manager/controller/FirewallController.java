// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.UpdateFirewallRulesRequest;
import com.cs.core.gateway.FirewallFilterService;
import com.cs.persistence.entity.FirewallRulesEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

@Tag(name = "防火墙管理接口")
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/firewall")
public class FirewallController {

    @Resource
    private FirewallFilterService firewallFilterService;

    @GetMapping(value = "/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<FirewallRulesEntity> queryFirewallRules() {
        return ResultEntity.success(firewallFilterService.getFirewallRules());
    }

    @PostMapping(value = "/save", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity saveFirewallRules(@Valid @RequestBody UpdateFirewallRulesRequest request) {
        firewallFilterService.updateFirewallRules(request);
        return ResultEntity.success();
    }

}
