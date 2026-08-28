// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.dto.ItemParam;
import com.cs.common.enums.DataTaskStatus;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.dto.*;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.dao.DataTaskDefDao;
import com.cs.persistence.dao.DataTaskJobDao;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.entity.DataTaskJobEntity;
import com.cs.persistence.util.JsonUtils;
import com.cs.persistence.util.PageUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager-facing orchestration over data task definitions and their job records:
 * validation/mirrors of the API assignment authoring flow (parameter declarations,
 * naming strategy, response type formats), snapshot capture at submit time so later
 * definition edits never mutate queued work, and lifecycle helpers around the
 * executor-side claiming workers.
 */
@Slf4j
@Service
public class DataTaskService {

    private static final int PREVIEW_DEFAULT_ROWS = 50;
    private static final int PREVIEW_MAX_ROWS = 200;

    @Resource
    private DataTaskDefDao dataTaskDefDao;
    @Resource
    private DataTaskJobDao dataTaskJobDao;
    @Resource
    private DataSourceDao dataSourceDao;
    @Resource
    private DataTaskSinkRegistry sinkRegistry;
    @Resource
    private DataTaskJobEngine dataTaskJobEngine;

    // ------------------------------------------------------------------ definitions

    public Long create(DataTaskSaveRequest request) {
        validate(request, true);
        DataTaskDefEntity entity = buildEntity(request);
        dataTaskDefDao.insert(entity);
        warnUnregisteredSink(entity.getSinkType());
        return entity.getId();
    }

    public void update(DataTaskSaveRequest request) {
        if (null == request.getId()) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "common.id.not.found", (Object) null);
        }
        DataTaskDefEntity exists = dataTaskDefDao.getById(request.getId());
        if (null == exists) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS,
                    "datatask.def.not.found", request.getId());
        }
        validate(request, false);
        DataTaskDefEntity entity = buildEntity(request);
        entity.setId(exists.getId());
        dataTaskDefDao.update(entity);
        warnUnregisteredSink(entity.getSinkType());
    }

    public void delete(Long id) {
        DataTaskDefEntity exists = dataTaskDefDao.getById(id);
        if (null == exists) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "datatask.def.not.found", id);
        }
        if (dataTaskJobDao.hasActiveByDef(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED,
                    "datatask.active.job.exists");
        }
        dataTaskDefDao.deleteById(id);
    }

    public DataTaskDetailResponse detail(Long id) {
        DataTaskDefEntity entity = dataTaskDefDao.getById(id);
        if (null == entity) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "datatask.def.not.found", id);
        }
        return toDetail(entity);
    }

    public java.util.List<DataTaskBaseResponse> list(DataTaskSearchRequest request) {
        return dataTaskDefDao.searchAll(request.getSearchText()).stream()
                .filter(e -> null == request.getEnabled()
                        || Objects.equals(e.getEnabled(), request.getEnabled()))
                .map(this::toBase)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------- running

    /** Submit an asynchronous execution; the record is picked up by any enabled worker. */
    public Long submit(DataTaskRunRequest request, String submittedBy) {
        DataTaskDefEntity def = requireEnabledDef(request.getDefId());
        Map<String, Object> bound = DataTaskParamBinder.bind(def.getParams(), request.getParams());
        DataTaskJobEntity job = DataTaskJobEntity.builder()
                .defId(def.getId())
                .defName(def.getName())
                .status(DataTaskStatus.PENDING)
                .snapshot(buildSnapshotJson(def))
                .paramsJson(JsonUtils.toJsonString(bound))
                .cancelRequested(Boolean.FALSE)
                .totalRows(0L)
                .submittedBy(submittedBy)
                .build();
        dataTaskJobDao.insert(job);
        log.info("Data task job {} submitted for task [{}] by {}", job.getId(), def.getName(), submittedBy);
        return job.getId();
    }

    /**
     * Synchronous preview without creating a job or touching delivery providers —
     * frontends use it to shape SQL and output profile before submitting real runs.
     */
    public Map<String, Object> preview(DataTaskRunRequest request) {
        DataTaskDefEntity def = requireEnabledDef(request.getDefId());
        Map<String, Object> bound = DataTaskParamBinder.bind(def.getParams(), request.getParams());
        int limit = null == request.getPreviewSize() || request.getPreviewSize() <= 0
                ? PREVIEW_DEFAULT_ROWS
                : Math.min(request.getPreviewSize(), PREVIEW_MAX_ROWS);
        try {
            return dataTaskJobEngine.debugPreview(def, bound, limit);
        } catch (CommonException ce) {
            throw ce;
        } catch (Exception e) {
            log.warn("Data task preview failed: {}", e.getMessage(), e);
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR,
                    "datatask.preview.failed", StringUtils.defaultString(e.getMessage()));
        }
    }

    public void cancel(Long jobId) {
        DataTaskJobEntity job = dataTaskJobDao.getById(jobId);
        if (null == job) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "datatask.job.not.found", jobId);
        }
        if (DataTaskStatus.PENDING == job.getStatus()) {
            if (!dataTaskJobDao.cancelIfPending(jobId)) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "datatask.cancel.state", job.getStatus().name());
            }
            return;
        }
        if (DataTaskStatus.RUNNING == job.getStatus()) {
            // cooperative: the executing worker observes the flag at its progress ticks
            dataTaskJobDao.markCancelRequested(jobId);
            return;
        }
        throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                "datatask.cancel.state", job.getStatus().name());
    }

    public DataTaskJobView jobView(Long jobId) {
        DataTaskJobEntity job = dataTaskJobDao.getById(jobId);
        if (null == job) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "datatask.job.not.found", jobId);
        }
        return toJobView(job);
    }

    public java.util.List<DataTaskJobView> jobViews(DataTaskJobSearchRequest request) {
        return dataTaskJobDao.search(request.getDefId(), request.getStatus()).stream()
                .map(this::toJobView)
                .collect(Collectors.toList());
    }

    // --------------------------------------------------------------------- internal

    private DataTaskDefEntity requireEnabledDef(Long defId) {
        if (null == defId) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "common.id.not.found", (Object) null);
        }
        DataTaskDefEntity def = dataTaskDefDao.getById(defId);
        if (null == def) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "datatask.def.not.found", defId);
        }
        if (!Boolean.TRUE.equals(def.getEnabled())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "datatask.disabled.submit", def.getName());
        }
        return def;
    }

    private void validate(DataTaskSaveRequest request, boolean create) {
        if (StringUtils.isBlank(request.getName())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "datatask.name.required");
        }
        DataTaskDefEntity sameName = dataTaskDefDao.getByName(request.getName());
        if (null != sameName && (create || !Objects.equals(sameName.getId(), request.getId()))) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS,
                    "datatask.name.exists", request.getName());
        }
        if (null == request.getDatasourceId()
                || null == dataSourceDao.getById(request.getDatasourceId())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "api.invalid.datasourceId");
        }
        if (StringUtils.isBlank(request.getSqlText())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "datatask.sql.required");
        }
        try {
            // construction itself parses dynamic tags; malformed scripts fail early here
            new com.cs.template.XmlSqlTemplate(request.getSqlText());
        } catch (Exception e) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                    "datatask.sql.invalid", StringUtils.defaultString(e.getMessage()));
        }
        if (StringUtils.isBlank(request.getSinkType())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "datatask.sink.required");
        }
        if (!CollectionUtils.isEmpty(request.getParams())) {
            for (ItemParam itemParam : request.getParams()) {
                // tasks are body-style parameters; POST keeps OBJECT-typed children legal
                itemParam.checkValid(com.cs.common.enums.HttpMethodEnum.POST);
            }
        }
        if (request.getColumnOrder() != null) {
            request.setColumnOrder(request.getColumnOrder().stream()
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList()));
        }
    }

    private DataTaskDefEntity buildEntity(DataTaskSaveRequest request) {
        DataTaskDefEntity entity = DataTaskDefEntity.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .datasourceId(request.getDatasourceId())
                .sqlText(request.getSqlText())
                .params(request.getParams())
                .columnAlias(null == request.getColumnAlias()
                        ? Collections.emptyMap() : request.getColumnAlias())
                .columnOrder(null == request.getColumnOrder()
                        ? Collections.emptyList() : request.getColumnOrder())
                .sinkType(request.getSinkType().trim())
                .sinkConfig(request.getSinkConfig())
                .dollarAllowed(Optional.ofNullable(request.getDollarAllowed()).orElse(Boolean.FALSE))
                .applyFormatToString(Optional.ofNullable(request.getApplyFormatToString()).orElse(Boolean.FALSE))
                .maxRows(request.getMaxRows())
                .enabled(Optional.ofNullable(request.getEnabled()).orElse(Boolean.TRUE))
                .build();
        entity.setNamingStrategy(null == request.getNamingStrategy()
                ? NamingStrategyEnum.CAMEL_CASE : request.getNamingStrategy());
        if (!CollectionUtils.isEmpty(request.getFormatMap())) {
            entity.setResponseFormat(request.getFormatMap().stream()
                    .filter(v -> null != v.getKey())
                    .collect(Collectors.toMap(DataTypeFormatMapValue::getKey,
                            DataTypeFormatMapValue::getValue, (a, b) -> a)));
        } else {
            entity.setResponseFormat(Collections.emptyMap());
        }
        return entity;
    }

    /** Snapshot excludes identity/audit fields — content only, replayable verbatim. */
    static String buildSnapshotJson(DataTaskDefEntity def) {
        DataTaskDefEntity spec = DataTaskDefEntity.builder()
                .name(def.getName())
                .datasourceId(def.getDatasourceId())
                .sqlText(def.getSqlText())
                .params(def.getParams())
                .namingStrategy(def.getNamingStrategy())
                .responseFormat(def.getResponseFormat())
                .columnAlias(def.getColumnAlias())
                .columnOrder(def.getColumnOrder())
                .applyFormatToString(def.getApplyFormatToString())
                .dollarAllowed(def.getDollarAllowed())
                .maxRows(def.getMaxRows())
                .sinkType(def.getSinkType())
                .sinkConfig(def.getSinkConfig())
                .enabled(def.getEnabled())
                .build();
        return JsonUtils.toJsonString(spec);
    }

    private void warnUnregisteredSink(String sinkType) {
        if (!sinkRegistry.knownTypes().contains(sinkType)) {
            log.warn("Sink type [{}] not resolvable on this node; ensure the extension jar is deployed "
                    + "on every executor before submitting such tasks", sinkType);
        }
    }

    private DataTaskBaseResponse toBase(DataTaskDefEntity entity) {
        return DataTaskBaseResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .datasourceId(entity.getDatasourceId())
                .sinkType(entity.getSinkType())
                .enabled(entity.getEnabled())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private DataTaskDetailResponse toDetail(DataTaskDefEntity entity) {
        List<DataTypeFormatMapValue> formatMap = new ArrayList<>();
        if (null != entity.getResponseFormat()) {
            for (Map.Entry<com.cs.common.enums.DataTypeFormatEnum, String> entry
                    : entity.getResponseFormat().entrySet()) {
                formatMap.add(DataTypeFormatMapValue.builder()
                        .key(entry.getKey())
                        .value(entry.getValue())
                        .remark(entry.getKey().getRemark())
                        .build());
            }
        }
        return DataTaskDetailResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .datasourceId(entity.getDatasourceId())
                .sqlText(entity.getSqlText())
                .params(entity.getParams())
                .namingStrategy(entity.getNamingStrategy())
                .formatMap(formatMap)
                .columnAlias(entity.getColumnAlias())
                .columnOrder(entity.getColumnOrder())
                .applyFormatToString(entity.getApplyFormatToString())
                .dollarAllowed(entity.getDollarAllowed())
                .maxRows(entity.getMaxRows())
                .sinkType(entity.getSinkType())
                .sinkConfig(entity.getSinkConfig())
                .enabled(entity.getEnabled())
                .build();
    }

    private DataTaskJobView toJobView(DataTaskJobEntity job) {
        Map<String, Object> info = Collections.emptyMap();
        if (StringUtils.isNotBlank(job.getArtifactInfo())) {
            try {
                info = JsonUtils.toBeanType(job.getArtifactInfo(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("Failed parsing artifact info of job {}: {}", job.getId(), e.getMessage());
            }
        }
        return DataTaskJobView.builder()
                .id(job.getId())
                .defId(job.getDefId())
                .defName(job.getDefName())
                .status(null == job.getStatus() ? null : job.getStatus().name())
                .totalRows(job.getTotalRows())
                .cancelRequested(job.getCancelRequested())
                .artifactUri(job.getArtifactUri())
                .artifactInfo(info)
                .errorMessage(job.getErrorMessage())
                .workerAddr(job.getWorkerAddr())
                .submittedBy(job.getSubmittedBy())
                .startTime(job.getStartTime())
                .finishTime(job.getFinishTime())
                .createTime(job.getCreateTime())
                .build();
    }
}
