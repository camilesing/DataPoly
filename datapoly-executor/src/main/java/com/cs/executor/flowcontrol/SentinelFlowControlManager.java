// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.flowcontrol;

import com.alibaba.csp.sentinel.*;
import com.alibaba.csp.sentinel.slots.block.*;
import com.alibaba.csp.sentinel.slots.block.flow.*;
import com.cs.common.consts.Constants;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.service.FlowControlLease;
import com.cs.common.service.FlowControlManger;
import com.cs.common.util.I18nUtils;
import com.cs.core.util.ResponseWriteUtils;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class SentinelFlowControlManager implements FlowControlManger {

    @Resource
    private ApiOnlineDao apiOnlineDao;

    /* Executed every minute */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${cron.flow.expression:0 0/1 * * * ?}")
    public void loadFlowRules() {
        try {
            doLoadFlowRules();
        } catch (Exception e) {
            log.error("load flow rules failed:{}", e.getMessage(), e);
        }
    }

    private void doLoadFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        for (ApiAssignmentEntity assignmentEntity : apiOnlineDao.listFlowControlAll()) {
            if (assignmentEntity.getFlowCount() <= 0) {
                continue;
            }
            HttpMethodEnum method = assignmentEntity.getMethod();
            String path = assignmentEntity.getPath();
            String resourceName = Constants.getResourceName(method.name(), path);
            FlowRule rule = new FlowRule(resourceName);
            if (RuleConstant.FLOW_GRADE_THREAD == assignmentEntity.getFlowGrade()) {
                rule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
            } else {
                rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            }
            rule.setCount(assignmentEntity.getFlowCount());
            rules.add(rule);
        }
        // loadRules with the full list also clears rules that were deleted since the last refresh
        FlowRuleManager.loadRules(rules);
        if (!rules.isEmpty() && log.isDebugEnabled()) {
            log.debug("Success refresh flow rules count: {}", rules.size());
        }
    }

    @Override
    public FlowControlLease checkFlowControl(String resourceName, HttpServletResponse response) throws IOException {
        Entry entry;
        try {
            // batch count 1: with 0 the QPS statistic never incremented, so no QPS rule
            // could ever trigger
            entry = SphU.entry(resourceName, 1, EntryType.IN);
        } catch (BlockException be) {
            this.handleBlockException(resourceName, response);
            return null;
        }
        // exit only when the guarded request completes (lease close): exiting here in the
        // finally made thread-grade rules a no-op and lost the response-time record
        return () -> entry.exit(1);
    }

    public void handleBlockException(String resourceName, HttpServletResponse response)
            throws IOException {
        ResponseWriteUtils.writeError(response, Constants.SC_TOO_MANY_REQUESTS,
                ResponseErrorCode.ERROR_TOO_MANY_REQUESTS,
                I18nUtils.getMessage("exception.ERROR_TOO_MANY_REQUESTS") + ": " + resourceName);
    }
}
