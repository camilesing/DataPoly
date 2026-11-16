// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.enums.DataTaskStatus;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.dto.*;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.entity.DataTaskJobEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

public class DataTaskServiceTest {

    private RecordingDefDao defDao;
    private RecordingJobDao jobDao;
    private DataTaskService service;

    @Before
    public void setUp() {
        defDao = new RecordingDefDao();
        jobDao = new RecordingJobDao();
        engine = new RecordingEngine();
        service = new DataTaskService();
        DataTaskTestSupport.setField(service, "dataTaskDefDao", defDao);
        DataTaskTestSupport.setField(service, "dataTaskJobDao", jobDao);
        DataTaskTestSupport.setField(service, "dataSourceDao", fixedDataSourceDao());
        DataTaskTestSupport.setField(service, "sinkRegistry",
                new DataTaskSinkRegistry(Collections.emptyList()));
        DataTaskTestSupport.setField(service, "dataTaskJobEngine", engine);
    }

    // ------------------------------------------------------------------ fakes

    private static class RecordingDefDao extends com.cs.persistence.dao.DataTaskDefDao {
        final Map<Long, DataTaskDefEntity> store = new LinkedHashMap<>();

        @Override
        public DataTaskDefEntity getById(Long id) {
            return store.get(id);
        }

        @Override
        public DataTaskDefEntity getByName(String name) {
            for (DataTaskDefEntity entity : store.values()) {
                if (Objects.equals(entity.getName(), name)) {
                    return entity;
                }
            }
            return null;
        }

        @Override
        public void insert(DataTaskDefEntity entity) {
            entity.setId((long) (store.size() + 1));
            store.put(entity.getId(), entity);
        }

        @Override
        public void update(DataTaskDefEntity entity) {
            store.put(entity.getId(), entity);
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        @Override
        public List<DataTaskDefEntity> searchAll(String searchText) {
            return new ArrayList<>(store.values());
        }
    }

    private static class RecordingJobDao extends com.cs.persistence.dao.DataTaskJobDao {
        final List<DataTaskJobEntity> inserted = new ArrayList<>();
        final List<Map<String, Object>> finishSuccessCalls = new ArrayList<>();
        final List<Map<String, Object>> finishFailureCalls = new ArrayList<>();
        final List<Boolean> pendingCancels = new ArrayList<>();
        final List<Boolean> runningCancelMarks = new ArrayList<>();
        boolean hasActive;
        boolean cancelIfPendingResult = true;
        List<DataTaskJobEntity> searchResult = Collections.emptyList();

        @Override
        public void insert(DataTaskJobEntity entity) {
            inserted.add(entity);
        }

        @Override
        public DataTaskJobEntity getById(Long id) {
            for (DataTaskJobEntity entity : inserted) {
                if (Objects.equals(entity.getId(), id)) {
                    return entity;
                }
            }
            return null;
        }

        @Override
        public boolean finishSuccess(Long id, long totalRows, String artifactUri, String artifactInfo,
                                     java.sql.Timestamp finishTime) {
            Map<String, Object> call = new HashMap<>();
            call.put("id", id);
            call.put("rows", totalRows);
            call.put("uri", artifactUri);
            call.put("info", artifactInfo);
            finishSuccessCalls.add(call);
            getById(id).setStatus(DataTaskStatus.SUCCESS);
            return true;
        }

        @Override
        public boolean finishFailure(Long id, String errorMessage, long totalRows,
                                     java.sql.Timestamp finishTime) {
            Map<String, Object> call = new HashMap<>();
            call.put("message", errorMessage);
            finishFailureCalls.add(call);
            return true;
        }

        @Override
        public boolean cancelIfPending(Long id) {
            pendingCancels.add(Boolean.TRUE);
            return cancelIfPendingResult;
        }

        @Override
        public boolean markCancelRequested(Long id) {
            runningCancelMarks.add(Boolean.TRUE);
            return true;
        }

        @Override
        public boolean hasActiveByDef(Long defId) {
            return hasActive;
        }

        @Override
        public List<DataTaskJobEntity> search(Long defId, DataTaskStatus status) {
            return searchResult;
        }
    }

    /** Captures preview calls instead of touching any real datasource. */
    private static class RecordingEngine extends DataTaskJobEngine {
        DataTaskDefEntity capturedDef;
        Map<String, Object> capturedParams;
        int capturedLimit = -1;
        Map<String, Object> result = new LinkedHashMap<>();
        Exception failure;

        @Override
        public Map<String, Object> debugPreview(DataTaskDefEntity def, Map<String, Object> boundParams, int limit) {
            capturedDef = def;
            capturedParams = boundParams;
            capturedLimit = limit;
            if (null != failure) {
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                throw new RuntimeException(failure);
            }
            return result;
        }
    }

    private RecordingEngine engine;

    private static DataSourceDao fixedDataSourceDao() {
        return new DataSourceDao() {
            @Override
            public DataSourceEntity getById(Long id) {
                if (null == id) {
                    return null;
                }
                return DataSourceEntity.builder()
                        .id(id)
                        .type(ProductTypeEnum.MYSQL)
                        .version("8.0")
                        .build();
            }
        };
    }

    // ------------------------------------------------------------------ helpers

    private DataTaskSaveRequest validRequest() {
        DataTaskSaveRequest request = DataTaskSaveRequest.builder()
                .name("order-snapshot")
                .description("demo")
                .datasourceId(7L)
                .sqlText("SELECT order_id AS raw_a, amount FROM t_order WHERE dt >= #{dt}")
                .formatMap(new ArrayList<>())
                .columnOrder(new ArrayList<>())
                .sinkType("stub")
                .build();
        DataTypeFormatMapValue formatValue = DataTypeFormatMapValue.builder()
                .key(com.cs.common.enums.DataTypeFormatEnum.TIMESTAMP)
                .value("yyyy-MM-dd HH:mm:ss")
                .build();
        request.getFormatMap().add(formatValue);
        return request;
    }

    private void seedDef(long id, String name, boolean enabled) {
        DataTaskSaveRequest request = validRequest();
        request.setName(name);
        Long created = service.create(request);
        Assert.assertNotNull(created);
        defDao.store.get(created).setId(id);
        defDao.store.get(created).setEnabled(enabled);
        defDao.store.put(id, defDao.store.remove(created));
    }

    // ------------------------------------------------------------------ cases

    @Test
    public void createAppliesDefaultsAndMapsResponseFormats() {
        Long id = service.create(validRequest());

        DataTaskDefEntity saved = defDao.store.get(id);
        Assert.assertEquals(NamingStrategyEnum.CAMEL_CASE, saved.getNamingStrategy());
        Assert.assertEquals(Boolean.TRUE, saved.getEnabled());
        Assert.assertEquals(Boolean.FALSE, saved.getDollarAllowed());
        Assert.assertEquals(Boolean.FALSE, saved.getApplyFormatToString());
        Assert.assertEquals(1, saved.getResponseFormat().size());
        Assert.assertEquals("yyyy-MM-dd HH:mm:ss",
                saved.getResponseFormat().get(com.cs.common.enums.DataTypeFormatEnum.TIMESTAMP));
    }

    @Test
    public void createRejectsDuplicateNameBlankSqlBlankSinkAndUnknownDatasource() {
        service.create(validRequest());

        // duplicate name carries its own error code
        try {
            service.create(validRequest());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }

        expectInvalid(new Runnable() {
            @Override
            public void run() {
                DataTaskSaveRequest r = validRequest();
                r.setName("another");
                r.setSqlText("   ");
                service.create(r);
            }
        });
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                DataTaskSaveRequest r = validRequest();
                r.setName("another");
                r.setSinkType("");
                service.create(r);
            }
        });
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                DataTaskSaveRequest r = validRequest();
                r.setName("another");
                r.setDatasourceId(null);
                service.create(r);
            }
        });
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void deleteIsBlockedWhileJobsActive() {
        Long id = service.create(validRequest());
        jobDao.hasActive = true;

        try {
            service.delete(id);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, e.getCode());
        }
        Assert.assertTrue(defDao.store.containsKey(id));
    }

    @Test
    public void createStoresPlainComparisonOperatorsVerbatim() {
        // an author writes SQL, not XML: '<' and '&' need no escape, and nothing is rewritten on the way in
        String sql = "SELECT order_id, amount FROM t_order WHERE dt >= #{dt} AND amount <= #{amount} "
                + "AND status <> #{status} AND name LIKE 'A&B%'";
        DataTaskSaveRequest request = validRequest();
        request.setName("plain-operators");
        request.setSqlText(sql);

        Long id = service.create(request);

        Assert.assertEquals(sql, defDao.store.get(id).getSqlText());
    }

    @Test
    public void updateKeepsPlainOperatorsAndDynamicTagsTogether() {
        Long id = service.create(validRequest());
        String sql = "SELECT order_id FROM t_order <where> "
                + "<if test='amount != null and amount <= 100'> AND amount <= #{amount} </if> "
                + "<if test='dt != null'> AND dt >= #{dt} </if> "
                + "</where>";
        DataTaskSaveRequest request = validRequest();
        request.setId(id);
        request.setSqlText(sql);

        service.update(request);

        Assert.assertEquals(sql, defDao.store.get(id).getSqlText());
        // the editor reads back exactly what was written, so an escaped form never reappears
        Assert.assertEquals(sql, service.detail(id).getSqlText());
    }

    @Test
    public void submitCapturesSnapshotBoundParamsAndUsername() {
        seedDef(11L, "task-a", true);

        Map<String, Object> body = new HashMap<>();
        body.put("dt", "2026-01-01");
        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(11L).params(body).build();

        Long jobId = service.submit(request, "alice");

        DataTaskJobEntity job = jobDao.getById(jobId);
        Assert.assertNotNull(job);
        Assert.assertEquals(DataTaskStatus.PENDING, job.getStatus());
        Assert.assertEquals("alice", job.getSubmittedBy());

        // snapshot round-trips the definition content independently of later edits
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = com.cs.persistence.util.JsonUtils.toBeanType(
                job.getSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        Assert.assertEquals("task-a", snapshot.get("name"));
        Assert.assertEquals("stub", snapshot.get("sinkType"));
    }

    @Test
    public void submitRejectsUnknownDefinition() {
        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(999L).build();
        try {
            service.submit(request, "alice");
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
        Assert.assertTrue(jobDao.inserted.isEmpty());
    }

    @Test
    public void submitRejectsDisabledDefinition() {
        seedDef(12L, "task-b", false);

        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(12L).build();
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.submit(request, "alice");
            }
        });
        Assert.assertTrue(jobDao.inserted.isEmpty());
    }

    @Test
    public void cancelFollowsStateMachine() {
        seedDef(13L, "task-c", true);
        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(13L).build();
        Long jobId = service.submit(request, "bob");

        service.cancel(jobId);
        Assert.assertEquals(1, jobDao.pendingCancels.size());

        // terminal records cannot be cancelled anymore
        jobDao.getById(jobId).setStatus(DataTaskStatus.SUCCESS);
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.cancel(jobId);
            }
        });
    }

    // ------------------------------------------------------- update / delete / detail

    @Test
    public void updateRejectsMissingIdAndUnknownDefinition() {
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.update(validRequest());
            }
        });

        DataTaskSaveRequest request = validRequest();
        request.setId(99L);
        try {
            service.update(request);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateOverwritesContentAndKeepsIdentity() {
        Long id = service.create(validRequest());

        DataTaskSaveRequest request = validRequest();
        request.setId(id);
        request.setDescription("rewritten");
        request.setSinkType("other-sink");
        service.update(request);

        DataTaskDefEntity saved = defDao.store.get(id);
        Assert.assertEquals("rewritten", saved.getDescription());
        Assert.assertEquals("other-sink", saved.getSinkType());
        Assert.assertEquals(Long.valueOf(id), saved.getId());
        Assert.assertEquals("same name against itself is not a conflict", "order-snapshot", saved.getName());
    }

    @Test
    public void updateRejectsNameTakenByAnotherDefinition() {
        service.create(validRequest()); // owns "order-snapshot"
        DataTaskSaveRequest other = validRequest();
        other.setName("other-task");
        Long otherId = service.create(other);

        DataTaskSaveRequest rename = validRequest();
        rename.setId(otherId);
        rename.setName("order-snapshot"); // the other definition wants the taken name
        try {
            service.update(rename);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
    }

    @Test
    public void deleteRemovesDefinitionOnceInactive() {
        Long id = service.create(validRequest());
        Assert.assertTrue(defDao.store.containsKey(id));

        service.delete(id);
        Assert.assertFalse("no active jobs: the definition row must actually go", defDao.store.containsKey(id));
    }

    @Test
    public void deleteUnknownDefinitionIsRejected() {
        try {
            service.delete(4242L);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void detailUnknownDefinitionIsRejected() {
        try {
            service.detail(4242L);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void detailMapsEntityOntoTheEditorView() {
        Long id = service.create(validRequest());

        DataTaskDetailResponse detail = service.detail(id);
        Assert.assertEquals("order-snapshot", detail.getName());
        Assert.assertEquals("SELECT order_id AS raw_a, amount FROM t_order WHERE dt >= #{dt}",
                detail.getSqlText());
        Assert.assertEquals(NamingStrategyEnum.CAMEL_CASE, detail.getNamingStrategy());
        Assert.assertEquals(Boolean.TRUE, detail.getEnabled());
        Assert.assertEquals(1, detail.getFormatMap().size());
        Assert.assertEquals(com.cs.common.enums.DataTypeFormatEnum.TIMESTAMP, detail.getFormatMap().get(0).getKey());
        Assert.assertEquals(com.cs.common.enums.DataTypeFormatEnum.TIMESTAMP.getRemark(),
                detail.getFormatMap().get(0).getRemark());
    }

    // ------------------------------------------------------- list

    @Test
    public void listFiltersByEnabledFlag() {
        DataTaskSaveRequest request = validRequest();
        request.setName("on");
        service.create(request);
        DataTaskSaveRequest off = validRequest();
        off.setName("off");
        off.setEnabled(Boolean.FALSE);
        service.create(off);

        DataTaskSearchRequest search = new DataTaskSearchRequest();
        Assert.assertEquals(2, service.list(search).size());

        search.setEnabled(Boolean.TRUE);
        List<DataTaskBaseResponse> enabled = service.list(search);
        Assert.assertEquals(1, enabled.size());
        Assert.assertEquals("on", enabled.get(0).getName());

        search.setEnabled(Boolean.FALSE);
        Assert.assertEquals("off", service.list(search).get(0).getName());
    }

    // ------------------------------------------------------- preview

    @Test
    public void previewBindsParametersAndCapsTheLimit() {
        seedDef(21L, "task-preview", true);
        com.cs.common.dto.ItemParam dt = new com.cs.common.dto.ItemParam();
        dt.setName("dt");
        dt.setType(com.cs.common.enums.ParamTypeEnum.STRING);
        dt.setIsArray(false);
        dt.setRequired(true);
        dt.setChildren(Collections.<com.cs.common.dto.BaseParam>emptyList());
        defDao.store.get(21L).setParams(new ArrayList<>(Collections.singletonList(dt)));
        Map<String, Object> params = new HashMap<>();
        params.put("dt", "2026-02-02");

        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(21L).params(params).build();
        service.preview(request);
        Assert.assertEquals(21L, (long) engine.capturedDef.getId());
        Assert.assertEquals(Collections.singletonMap("dt", "2026-02-02"), engine.capturedParams);
        Assert.assertEquals("null previewSize falls back to the default", 50, engine.capturedLimit);

        request.setPreviewSize(0);
        service.preview(request);
        Assert.assertEquals("non-positive previewSize also falls back", 50, engine.capturedLimit);

        request.setPreviewSize(5000);
        service.preview(request);
        Assert.assertEquals("oversized previewSize is capped", 200, engine.capturedLimit);

        request.setPreviewSize(7);
        service.preview(request);
        Assert.assertEquals(7, engine.capturedLimit);
    }

    @Test
    public void previewPassesTheEngineResultThrough() {
        seedDef(22L, "task-preview2", true);
        engine.result.put("totalPreviewed", 3L);

        Map<String, Object> view = service.preview(DataTaskRunRequest.builder().defId(22L).build());
        Assert.assertEquals(3L, view.get("totalPreviewed"));
    }

    @Test
    public void previewRejectsDisabledDefinition() {
        seedDef(23L, "task-preview3", false);
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.preview(DataTaskRunRequest.builder().defId(23L).build());
            }
        });
    }

    @Test
    public void previewWrapsEngineFailuresButKeepsCommonExceptions() {
        seedDef(24L, "task-preview4", true);

        engine.failure = new IllegalStateException("connection refused");
        try {
            service.preview(DataTaskRunRequest.builder().defId(24L).build());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }

        engine.failure = new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "common.datasource.not.found");
        try {
            service.preview(DataTaskRunRequest.builder().defId(24L).build());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals("CommonException crosses unwrapped", ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    // ------------------------------------------------------- cancel / job views

    @Test
    public void cancelUnknownJobIsRejected() {
        try {
            service.cancel(404L);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void cancelPendingFailsWhenTheRaceLostTheRow() {
        seedDef(25L, "task-cancel1", true);
        Long jobId = service.submit(DataTaskRunRequest.builder().defId(25L).build(), "bob");
        jobDao.cancelIfPendingResult = false; // a worker claimed the row between read and cancel

        try {
            service.cancel(jobId);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void cancelRunningMarksCooperatively() {
        seedDef(26L, "task-cancel2", true);
        Long jobId = service.submit(DataTaskRunRequest.builder().defId(26L).build(), "bob");
        jobDao.getById(jobId).setStatus(DataTaskStatus.RUNNING);

        service.cancel(jobId);
        Assert.assertEquals(1, jobDao.runningCancelMarks.size());
        Assert.assertEquals("no direct cancel attempted for RUNNING rows", 0, jobDao.pendingCancels.size());
    }

    @Test
    public void jobViewParsesArtifactInfoAndSurvivesBadJson() {
        seedDef(27L, "task-view", true);
        Long jobId = service.submit(DataTaskRunRequest.builder().defId(27L).build(), "bob");
        DataTaskJobEntity job = jobDao.getById(jobId);
        job.setArtifactUri("oss://bucket/x");
        job.setArtifactInfo("{\"rows\":9,\"provider\":\"stub\"}");

        DataTaskJobView view = service.jobView(jobId);
        Assert.assertEquals(jobId, view.getId());
        Assert.assertEquals(27L, (long) view.getDefId());
        Assert.assertEquals("task-view", view.getDefName());
        Assert.assertEquals(DataTaskStatus.PENDING.name(), view.getStatus());
        Assert.assertEquals("oss://bucket/x", view.getArtifactUri());
        Assert.assertEquals(9, view.getArtifactInfo().get("rows"));
        Assert.assertEquals("stub", view.getArtifactInfo().get("provider"));

        job.setArtifactInfo("not-json{");
        Assert.assertTrue("broken artifact info degrades to an empty map",
                service.jobView(jobId).getArtifactInfo().isEmpty());
    }

    @Test
    public void jobViewUnknownJobIsRejected() {
        try {
            service.jobView(404L);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void jobViewsMapsEveryRecordOfTheSearch() {
        DataTaskJobEntity job = DataTaskJobEntity.builder()
                .id(31L).defId(5L).defName("t").status(DataTaskStatus.RUNNING)
                .cancelRequested(Boolean.TRUE).errorMessage("halfway").build();
        jobDao.searchResult = Collections.singletonList(job);

        DataTaskJobSearchRequest request = new DataTaskJobSearchRequest();
        request.setDefId(5L);
        request.setStatus(DataTaskStatus.RUNNING);
        List<DataTaskJobView> views = service.jobViews(request);

        Assert.assertEquals(1, views.size());
        Assert.assertEquals(Long.valueOf(31L), views.get(0).getId());
        Assert.assertEquals(Boolean.TRUE, views.get(0).getCancelRequested());
        Assert.assertEquals("RUNNING", views.get(0).getStatus());
        Assert.assertEquals("halfway", views.get(0).getErrorMessage());
    }

    @Test
    public void submitRejectsMissingDefinitionId() {
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.submit(DataTaskRunRequest.builder().build(), "bob");
            }
        });
    }

    // ------------------------------------------------------- validation corners

    @Test
    public void createRejectsBlankName() {
        DataTaskSaveRequest request = validRequest();
        request.setName("  ");
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.create(request);
            }
        });
    }

    @Test
    public void createRejectsUnparsableTemplate() {
        DataTaskSaveRequest request = validRequest();
        request.setName("broken-template");
        request.setSqlText("<if test='x'>never closed");
        try {
            service.create(request);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createRejectsInvalidParamDeclaration() {
        DataTaskSaveRequest request = validRequest();
        request.setName("bad-params");
        com.cs.common.dto.ItemParam blank = new com.cs.common.dto.ItemParam();
        blank.setName(" ");
        blank.setType(com.cs.common.enums.ParamTypeEnum.STRING);
        blank.setChildren(Collections.<com.cs.common.dto.BaseParam>emptyList());
        request.setParams(new ArrayList<>(Collections.singletonList(blank)));
        try {
            service.create(request);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }

        DataTaskSaveRequest objectless = validRequest();
        objectless.setName("object-without-children");
        com.cs.common.dto.ItemParam objectParam = new com.cs.common.dto.ItemParam();
        objectParam.setName("obj");
        objectParam.setType(com.cs.common.enums.ParamTypeEnum.OBJECT);
        objectParam.setChildren(Collections.<com.cs.common.dto.BaseParam>emptyList());
        objectless.setParams(new ArrayList<>(Collections.singletonList(objectParam)));
        expectInvalid(new Runnable() {
            @Override
            public void run() {
                service.create(objectless);
            }
        });
    }

    @Test
    public void createNormalizesColumnOrderAndAlias() {
        DataTaskSaveRequest request = validRequest();
        request.setName("ordered");
        request.setColumnAlias(null);
        request.setColumnOrder(Arrays.asList("b", " ", "b", null, "a"));

        Long id = service.create(request);

        DataTaskDefEntity saved = defDao.store.get(id);
        Assert.assertEquals(Collections.emptyMap(), saved.getColumnAlias());
        Assert.assertEquals("blank and duplicate entries are dropped", Arrays.asList("b", "a"),
                saved.getColumnOrder());
    }

    @Test
    public void createSkipsFormatEntriesWithoutKeyAndDeduplicates() {
        DataTaskSaveRequest request = validRequest();
        request.setName("formats");
        DataTypeFormatMapValue noKey = DataTypeFormatMapValue.builder().value("ignored").build();
        DataTypeFormatMapValue second = DataTypeFormatMapValue.builder()
                .key(com.cs.common.enums.DataTypeFormatEnum.TIMESTAMP)
                .value("yyyy/MM/dd").build();
        request.getFormatMap().add(noKey);
        request.getFormatMap().add(second);

        Long id = service.create(request);

        Map<com.cs.common.enums.DataTypeFormatEnum, String> formats = defDao.store.get(id).getResponseFormat();
        Assert.assertEquals(1, formats.size());
        Assert.assertEquals("first mapping wins on duplicate keys", "yyyy-MM-dd HH:mm:ss",
                formats.get(com.cs.common.enums.DataTypeFormatEnum.TIMESTAMP));
    }

    @Test
    public void createWithoutFormatMapKeepsAnEmptyResponseFormat() {
        DataTaskSaveRequest request = validRequest();
        request.setName("no-formats");
        request.setFormatMap(null);

        Long id = service.create(request);
        Assert.assertEquals(Collections.emptyMap(), defDao.store.get(id).getResponseFormat());
    }

    @Test
    public void createHonorsExplicitBooleansAndNaming() {
        DataTaskSaveRequest request = validRequest();
        request.setName("explicit");
        request.setNamingStrategy(NamingStrategyEnum.NONE);
        request.setDollarAllowed(Boolean.TRUE);
        request.setApplyFormatToString(Boolean.TRUE);
        request.setEnabled(Boolean.FALSE);
        request.setMaxRows(500L);

        DataTaskDefEntity saved = defDao.store.get(service.create(request));
        Assert.assertEquals(NamingStrategyEnum.NONE, saved.getNamingStrategy());
        Assert.assertEquals(Boolean.TRUE, saved.getDollarAllowed());
        Assert.assertEquals(Boolean.TRUE, saved.getApplyFormatToString());
        Assert.assertEquals(Boolean.FALSE, saved.getEnabled());
        Assert.assertEquals(Long.valueOf(500L), saved.getMaxRows());
    }

    @Test
    public void createTrimsNameAndSinkType() {
        DataTaskSaveRequest request = validRequest();
        request.setName("  spaced  ");
        request.setSinkType("  sink  ");

        DataTaskDefEntity saved = defDao.store.get(service.create(request));
        Assert.assertEquals("spaced", saved.getName());
        Assert.assertEquals("sink", saved.getSinkType());
    }

    // ------------------------------------------------------- remaining branch corners

    @Test
    public void createAcceptsAValidParamDeclarationAndExitsTheLoop() {
        DataTaskSaveRequest request = validRequest();
        request.setName("with-valid-params");
        com.cs.common.dto.ItemParam ok = new com.cs.common.dto.ItemParam();
        ok.setName("dt");
        ok.setType(com.cs.common.enums.ParamTypeEnum.STRING);
        ok.setIsArray(false);
        ok.setRequired(true);
        ok.setChildren(Collections.<com.cs.common.dto.BaseParam>emptyList());
        request.setParams(new ArrayList<>(Collections.singletonList(ok)));

        Long id = service.create(request);

        DataTaskDefEntity saved = defDao.store.get(id);
        Assert.assertEquals(1, saved.getParams().size());
        // checkValid ran through the whole loop without throwing
        Assert.assertEquals("dt", saved.getParams().get(0).getName());
    }

    @Test
    public void createWithoutColumnOrderKeepsItNullSafe() {
        DataTaskSaveRequest request = validRequest();
        request.setName("no-order");
        request.setColumnAlias(null);
        request.setColumnOrder(null); // both shaping knobs absent

        DataTaskDefEntity saved = defDao.store.get(service.create(request));
        Assert.assertEquals(Collections.emptyMap(), saved.getColumnAlias());
        Assert.assertEquals(Collections.emptyList(), saved.getColumnOrder());
    }

    @Test
    public void createWithAKnownSinkTypeSkipsTheWarning() {
        // a registered sink type: warnUnregisteredSink takes the no-warning branch
        com.cs.common.datatask.DataTaskSink stubSink = new com.cs.common.datatask.DataTaskSink() {
            @Override
            public String type() {
                return "stub";
            }

            @Override
            public com.cs.common.datatask.SinkSession openSession(
                    com.cs.common.datatask.SinkRequest request) {
                throw new UnsupportedOperationException("not used here");
            }
        };
        DataTaskTestSupport.setField(service, "sinkRegistry",
                new DataTaskSinkRegistry(Collections.singletonList(stubSink)));

        Assert.assertNotNull(service.create(validRequest()));
    }

    @Test
    public void detailOfAnEntityWithoutResponseFormatYieldsNoFormats() {
        // an entity stored without formats (e.g. legacy rows) maps to an empty format list
        DataTaskDefEntity bare = DataTaskDefEntity.builder()
                .id(88L).name("bare").datasourceId(7L).sqlText("SELECT 1")
                .sinkType("stub").enabled(Boolean.TRUE).build();
        defDao.store.put(88L, bare);

        DataTaskDetailResponse detail = service.detail(88L);
        Assert.assertEquals("bare", detail.getName());
        Assert.assertTrue(detail.getFormatMap().isEmpty());
    }

    @Test
    public void jobViewOfAJobWithoutStatusMapsItToNull() {
        DataTaskJobEntity job = DataTaskJobEntity.builder()
                .id(91L).defId(5L).defName("t").cancelRequested(Boolean.FALSE).build();
        jobDao.inserted.add(job);

        DataTaskJobView view = service.jobView(91L);
        Assert.assertNull("a null status must stay null, never NPE", view.getStatus());
        Assert.assertEquals(Long.valueOf(91L), view.getId());
        Assert.assertTrue(view.getArtifactInfo().isEmpty());
    }

    @Test
    public void createRejectsANullDatasourceIdBeforeTouchingTheDao() {
        DataTaskSaveRequest request = validRequest();
        request.setDatasourceId(null);
        try {
            service.create(request);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }

        // a non-null id the DAO cannot resolve is rejected the same way
        DataTaskTestSupport.setField(service, "dataSourceDao", new DataSourceDao() {
            @Override
            public DataSourceEntity getById(Long id) {
                return null;
            }
        });
        request.setDatasourceId(404L);
        try {
            service.create(request);
            Assert.fail("expected CommonException for an unknown datasource id");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createKeepsAnExplicitlyProvidedAliasMap() {
        DataTaskSaveRequest request = validRequest();
        request.setColumnAlias(Collections.singletonMap("amount", "total"));

        DataTaskDefEntity saved = defDao.store.get(service.create(request));
        Assert.assertEquals(Collections.singletonMap("amount", "total"), saved.getColumnAlias());
    }
}
