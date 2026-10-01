// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.upgrade;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.persistence.dao.*;
import com.cs.persistence.entity.*;
import com.cs.persistence.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Upgrades VCS data automatically
 *
 * @author suntang
 */
@Slf4j
@Component
public class VersionUpgradeRunner implements ApplicationRunner {

    private static final String COMMIT_INIT_DESC = "First Commit By Upgrade.";

    @Resource
    private ApiAssignmentDao apiAssignmentDao;
    @Resource
    private VersionCommitDao versionCommitDao;
    @Resource
    private ApiOnlineDao apiOnlineDao;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        VersionUpgradeRunner runner = SpringUtil.getBean(VersionUpgradeRunner.class);
        log.info("Startup upgrade version control now.");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("UpgradeVersion");
        List<Long> idList = apiAssignmentDao.getUpgradeOnlineAssignments();
        List<Long> upgradedIds = new ArrayList<>(idList.size());
        for (Long id : idList) {
            // one poison record must not abort manager startup: failures keep their upgrade
            // flag and are retried on the next startup
            try {
                if (null == versionCommitDao.getLatestVersion(id)) {
                    ApiAssignmentEntity assignment = apiAssignmentDao.getById(id, true);
                    if (null == assignment) {
                        log.warn("API assignment {} disappeared before upgrade, skip it.", id);
                    } else {
                        runner.upgradeApiAssignment(assignment);
                    }
                }
                upgradedIds.add(id);
            } catch (Exception e) {
                log.error("Upgrade version control failed for API assignment {}, keep flag for next startup.", id, e);
            }
        }
        apiAssignmentDao.resetUpgradeOnlineAssignments(upgradedIds);
        stopWatch.stop();
        log.info("Success upgrade version control for {} APIs ({} kept for retry), total cost {} ms .",
                upgradedIds.size(), idList.size() - upgradedIds.size(), stopWatch.getTotalTimeMillis());
    }

    @Transactional(rollbackFor = Exception.class)
    public void upgradeApiAssignment(ApiAssignmentEntity assignment) {
        String content = JsonUtils.toJsonString(assignment);
        Long bizId = assignment.getId();
        VersionCommitEntity commitEntity = versionCommitDao.createVersion(bizId, COMMIT_INIT_DESC, content);
        ApiOnlineEntity onlineEntity = ApiOnlineEntity.builder()
                .name(assignment.getName())
                .method(assignment.getMethod())
                .path(assignment.getPath())
                .apiId(assignment.getId())
                .groupId(assignment.getGroupId())
                .moduleId(assignment.getModuleId())
                .datasourceId(assignment.getDatasourceId())
                .open(assignment.getOpen())
                .alarm(assignment.getAlarm())
                .flowStatus(assignment.getFlowStatus())
                .commitId(commitEntity.getId())
                .version(commitEntity.getVersion())
                .content(content)
                .build();
        apiOnlineDao.upsert(onlineEntity);
    }
}
