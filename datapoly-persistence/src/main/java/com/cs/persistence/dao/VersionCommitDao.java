// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cs.common.exception.*;
import com.cs.persistence.entity.VersionCommitEntity;
import com.cs.persistence.mapper.VersionCommitMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Repository
public class VersionCommitDao {

    private static final int CREATE_VERSION_MAX_ATTEMPTS = 3;

    @Resource
    private VersionCommitMapper versionCommitMapper;

    /**
     * Create a version (max(version)+1, A4): bounded retry on api_id_version unique key conflicts from concurrent publish.
     * Each attempt runs in its own new transaction (REQUIRES_NEW) — a failed PG transaction is fully aborted, so the retry must start outside it;
     * self-invocation via the Spring proxy keeps the transaction annotation effective (see VersionUpgradeRunner).
     */
    public VersionCommitEntity createVersion(Long bizId, String description, String content) {
        VersionCommitDao self = SpringUtil.getBean(VersionCommitDao.class);
        for (int attempt = 1; ; attempt++) {
            try {
                return self.doCreateVersion(bizId, description, content);
            } catch (DuplicateKeyException e) {
                if (attempt >= CREATE_VERSION_MAX_ATTEMPTS) {
                    throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "api.version.concurrent.conflict");
                }
                log.warn("Concurrent version create conflict for bizId={}, retrying (attempt {}/{}).",
                        bizId, attempt, CREATE_VERSION_MAX_ATTEMPTS);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public VersionCommitEntity doCreateVersion(Long bizId, String description, String content) {
        Integer currMaxVersion = versionCommitMapper.getMaxVersion(bizId);
        VersionCommitEntity entity = VersionCommitEntity.builder()
                .bizId(bizId)
                .version(Optional.ofNullable(currMaxVersion).orElse(0) + 1)
                .description(description)
                .content(content)
                .build();
        versionCommitMapper.insert(entity);
        return entity;
    }

    public VersionCommitEntity getLatestVersion(Long bizId) {
        return versionCommitMapper.getLatestVersion(bizId);
    }

    public VersionCommitEntity getByCommitId(Long commitId) {
        return versionCommitMapper.selectById(commitId);
    }

    public List<VersionCommitEntity> getVersionList(Long bizId, boolean withContent) {
        List<SFunction<VersionCommitEntity, ?>> columns = new ArrayList<>();
        columns.add(VersionCommitEntity::getId);
        columns.add(VersionCommitEntity::getBizId);
        columns.add(VersionCommitEntity::getVersion);
        columns.add(VersionCommitEntity::getDescription);
        columns.add(VersionCommitEntity::getCreateTime);
        if (withContent) {
            columns.add(VersionCommitEntity::getContent);
        }
        return versionCommitMapper.selectList(
                Wrappers.<VersionCommitEntity>lambdaQuery()
                        .select(columns)
                        .eq(VersionCommitEntity::getBizId, bizId)
                        .orderByDesc(VersionCommitEntity::getVersion)
        );
    }
}
