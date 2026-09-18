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
        service = new DataTaskService();
        DataTaskTestSupport.setField(service, "dataTaskDefDao", defDao);
        DataTaskTestSupport.setField(service, "dataTaskJobDao", jobDao);
        DataTaskTestSupport.setField(service, "dataSourceDao", fixedDataSourceDao());
        DataTaskTestSupport.setField(service, "sinkRegistry",
                new DataTaskSinkRegistry(Collections.emptyList()));
        DataTaskTestSupport.setField(service, "dataTaskJobEngine", new DataTaskJobEngine());
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
            return true;
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
    }

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
}
