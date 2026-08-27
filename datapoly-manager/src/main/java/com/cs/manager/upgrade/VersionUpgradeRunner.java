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

import javax.annotation.Resource;
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
        for (Long id : idList) {
            if (null == versionCommitDao.getLatestVersion(id)) {
                runner.upgradeApiAssignment(apiAssignmentDao.getById(id, true));
            }
        }
        apiAssignmentDao.resetUpgradeOnlineAssignments(idList);
        stopWatch.stop();
        log.info("Success upgrade version control for {} APIs, total cost {} ms .",
                idList.size(), stopWatch.getTotalTimeMillis());
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
