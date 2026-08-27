// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cs.common.enums.DataTaskStatus;
import com.cs.persistence.entity.DataTaskJobEntity;
import com.cs.persistence.mapper.DataTaskJobMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

@Repository
public class DataTaskJobDao {

    @Resource
    private DataTaskJobMapper dataTaskJobMapper;

    public DataTaskJobEntity getById(Long id) {
        return dataTaskJobMapper.selectById(id);
    }

    public void insert(DataTaskJobEntity entity) {
        dataTaskJobMapper.insert(entity);
    }

    /**
     * Claim up to {@code limit} PENDING jobs for one worker inside a single
     * transaction: candidates are locked via SKIP LOCKED, then flipped to RUNNING
     * before commit so no other instance can take them.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> claimPending(int limit, String workerAddr, Timestamp startTime, Timestamp leaseExpireAt) {
        List<Long> ids = dataTaskJobMapper.selectClaimableIds(limit);
        if (CollectionUtils.isEmpty(ids)) {
            return ids;
        }
        for (Long id : ids) {
            UpdateWrapper<DataTaskJobEntity> wrapper = new UpdateWrapper<>();
            wrapper.lambda().eq(DataTaskJobEntity::getId, id)
                    .eq(DataTaskJobEntity::getStatus, DataTaskStatus.PENDING);
            dataTaskJobMapper.update(newRunning(workerAddr, startTime, leaseExpireAt), wrapper);
        }
        return ids;
    }

    private DataTaskJobEntity newRunning(String workerAddr, Timestamp startTime, Timestamp leaseExpireAt) {
        return DataTaskJobEntity.builder()
                .status(DataTaskStatus.RUNNING)
                .workerAddr(workerAddr)
                .startTime(startTime)
                .leaseExpireAt(leaseExpireAt)
                .build();
    }

    /** Progress + lease refresh while RUNNING; also picks up nothing when status moved on */
    public boolean heartbeat(Long id, long totalRows, Timestamp leaseExpireAt) {
        UpdateWrapper<DataTaskJobEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda().eq(DataTaskJobEntity::getId, id)
                .eq(DataTaskJobEntity::getStatus, DataTaskStatus.RUNNING);
        DataTaskJobEntity patch = DataTaskJobEntity.builder()
                .totalRows(totalRows)
                .leaseExpireAt(leaseExpireAt)
                .build();
        return dataTaskJobMapper.update(patch, wrapper) > 0;
    }

    public boolean finishSuccess(Long id, long totalRows, String artifactUri, String artifactInfo, Timestamp finishTime) {
        UpdateWrapper<DataTaskJobEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda().eq(DataTaskJobEntity::getId, id)
                .eq(DataTaskJobEntity::getStatus, DataTaskStatus.RUNNING);
        DataTaskJobEntity patch = DataTaskJobEntity.builder()
                .status(DataTaskStatus.SUCCESS)
                .totalRows(totalRows)
                .artifactUri(artifactUri)
                .artifactInfo(artifactInfo)
                .finishTime(finishTime)
                .leaseExpireAt(null)
                .build();
        // explicit null clear for the lease column needs set-sql, builder keeps it untouched
        wrapper.set("lease_expire_at", null);
        return dataTaskJobMapper.update(patch, wrapper) > 0;
    }

    public boolean finishFailure(Long id, String errorMessage, long totalRows, Timestamp finishTime) {
        UpdateWrapper<DataTaskJobEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda().eq(DataTaskJobEntity::getId, id)
                .eq(DataTaskJobEntity::getStatus, DataTaskStatus.RUNNING);
        wrapper.set("lease_expire_at", null);
        DataTaskJobEntity patch = DataTaskJobEntity.builder()
                .status(DataTaskStatus.FAILED)
                .errorMessage(errorMessage)
                .totalRows(totalRows)
                .finishTime(finishTime)
                .build();
        return dataTaskJobMapper.update(patch, wrapper) > 0;
    }

    /** Cooperative cancel flag for a running job; false when not running anymore */
    public boolean markCancelRequested(Long id) {
        UpdateWrapper<DataTaskJobEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda().eq(DataTaskJobEntity::getId, id)
                .eq(DataTaskJobEntity::getStatus, DataTaskStatus.RUNNING);
        DataTaskJobEntity patch = DataTaskJobEntity.builder().cancelRequested(Boolean.TRUE).build();
        return dataTaskJobMapper.update(patch, wrapper) > 0;
    }

    /** Direct cancel still possible only for queued jobs */
    public boolean cancelIfPending(Long id) {
        UpdateWrapper<DataTaskJobEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda().eq(DataTaskJobEntity::getId, id)
                .eq(DataTaskJobEntity::getStatus, DataTaskStatus.PENDING);
        DataTaskJobEntity patch = DataTaskJobEntity.builder()
                .status(DataTaskStatus.CANCELED)
                .finishTime(new Timestamp(System.currentTimeMillis()))
                .cancelRequested(Boolean.FALSE)
                .build();
        return dataTaskJobMapper.update(patch, wrapper) > 0;
    }

    public int reapExpired(String message) {
        return dataTaskJobMapper.failExpiredLeases(message);
    }

    public boolean hasActiveByDef(Long defId) {
        QueryWrapper<DataTaskJobEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DataTaskJobEntity::getDefId, defId)
                .in(DataTaskJobEntity::getStatus, Arrays.asList(DataTaskStatus.PENDING, DataTaskStatus.RUNNING));
        return dataTaskJobMapper.selectCount(queryWrapper) > 0;
    }

    public List<DataTaskJobEntity> search(Long defId, DataTaskStatus status) {
        QueryWrapper<DataTaskJobEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(null != defId, DataTaskJobEntity::getDefId, defId)
                .eq(null != status, DataTaskJobEntity::getStatus, null == status ? null : status.name())
                .orderByDesc(DataTaskJobEntity::getId);
        return dataTaskJobMapper.selectList(queryWrapper);
    }
}
