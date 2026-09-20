// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.common.enums.DataTaskStatus;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.DataTaskJobEntity;
import com.cs.persistence.mapper.DataTaskJobMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.Assert.*;

public class DataTaskJobDaoTest {

    private static class Recorder {
        final Map<String, List<Object[]>> calls = new LinkedHashMap<>();
        private final Map<String, Queue<Object>> stubs = new HashMap<>();

        @SuppressWarnings("unchecked")
        <M> M create(Class<M> type) {
            return (M) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, m, a) -> {
                        calls.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(a);
                        Class<?> rt = m.getReturnType();
                        Queue<Object> queue = stubs.get(m.getName());
                        if (queue != null && !queue.isEmpty()) {
                            Object result = queue.poll();
                            if (result instanceof RuntimeException) {
                                throw (RuntimeException) result;
                            }
                            if (result == null && rt.isPrimitive()) {
                                return defaultValue(rt);
                            }
                            return result;
                        }
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        if (rt == long.class) {
                            return 0L;
                        }
                        return null;
                    });
        }

        void stub(String method, Object... results) {
            Queue<Object> queue = stubs.computeIfAbsent(method, k -> new LinkedList<>());
            for (Object result : results) {
                queue.add(result);
            }
        }


        private static Object defaultValue(Class<?> rt) {
            if (rt == boolean.class) {
                return false;
            }
            if (rt == int.class) {
                return 0;
            }
            if (rt == long.class) {
                return 0L;
            }
            return null;
        }

        int count(String method) {
            return calls.getOrDefault(method, Collections.emptyList()).size();
        }

        Object[] argsOf(String method) {
            return calls.get(method).get(0);
        }
    }

    private Recorder recorder;
    private DataTaskJobDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.DataTaskJobEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new DataTaskJobDao();
        DataTaskTestSupport.setField(dao, "dataTaskJobMapper", recorder.create(DataTaskJobMapper.class));
    }

    @Test
    public void testGetByIdAndInsert() {
        DataTaskJobEntity entity = DataTaskJobEntity.builder().id(5L).build();
        recorder.stub("selectById", entity);
        assertSame(entity, dao.getById(5L));

        DataTaskJobEntity fresh = DataTaskJobEntity.builder().build();
        dao.insert(fresh);
        assertEquals(1, recorder.count("insert"));
        assertSame(fresh, recorder.argsOf("insert")[0]);
    }

    @Test
    public void testClaimPendingWithNoCandidates() {
        recorder.stub("selectClaimableIds", Collections.<Long>emptyList());
        assertTrue(dao.claimPending(3, "worker", new Timestamp(0), new Timestamp(1)).isEmpty());
        assertEquals(0, recorder.count("update"));
    }

    @Test
    public void testClaimPendingOnlyKeepsActuallyFlippedIds() {
        recorder.stub("selectClaimableIds", Arrays.asList(1L, 2L, 3L));
        recorder.stub("update", 1, 0, 1);
        List<Long> claimed = dao.claimPending(3, "worker-1", new Timestamp(0), new Timestamp(9));
        assertEquals(Arrays.asList(1L, 3L), claimed);

        DataTaskJobEntity patch = (DataTaskJobEntity) recorder.argsOf("update")[0];
        assertEquals(DataTaskStatus.RUNNING, patch.getStatus());
        assertEquals("worker-1", patch.getWorkerAddr());
        assertEquals(0L, patch.getStartTime().getTime());
        assertEquals(9L, patch.getLeaseExpireAt().getTime());
    }

    @Test
    public void testHeartbeatRefreshesProgressAndLease() {
        recorder.stub("update", 1, 0);
        assertTrue(dao.heartbeat(7L, 12345L, new Timestamp(42)));
        DataTaskJobEntity patch = (DataTaskJobEntity) recorder.argsOf("update")[0];
        assertEquals(Long.valueOf(12345L), patch.getTotalRows());
        assertEquals(42L, patch.getLeaseExpireAt().getTime());
        assertFalse(dao.heartbeat(7L, 1L, new Timestamp(1)));
    }

    @Test
    public void testFinishSuccessClearsLeaseAndStoresArtifact() {
        recorder.stub("update", 1);
        assertTrue(dao.finishSuccess(7L, 10L, "file://out.csv", "{\"rows\":10}", new Timestamp(5)));
        DataTaskJobEntity patch = (DataTaskJobEntity) recorder.argsOf("update")[0];
        assertEquals(DataTaskStatus.SUCCESS, patch.getStatus());
        assertEquals(Long.valueOf(10L), patch.getTotalRows());
        assertEquals("file://out.csv", patch.getArtifactUri());
        assertEquals("{\"rows\":10}", patch.getArtifactInfo());
        assertEquals(5L, patch.getFinishTime().getTime());
        assertNull(patch.getLeaseExpireAt());
    }

    @Test
    public void testFinishFailureRecordsError() {
        recorder.stub("update", 1);
        assertTrue(dao.finishFailure(7L, "boom", 5L, new Timestamp(5)));
        DataTaskJobEntity patch = (DataTaskJobEntity) recorder.argsOf("update")[0];
        assertEquals(DataTaskStatus.FAILED, patch.getStatus());
        assertEquals("boom", patch.getErrorMessage());
        assertEquals(Long.valueOf(5L), patch.getTotalRows());
    }

    @Test
    public void testCancelFlags() {
        recorder.stub("update", 1, 1);
        assertTrue(dao.markCancelRequested(7L));
        assertEquals(Boolean.TRUE, ((DataTaskJobEntity) recorder.argsOf("update")[0]).getCancelRequested());

        assertTrue(dao.cancelIfPending(7L));
        DataTaskJobEntity patch = (DataTaskJobEntity) recorder.calls.get("update").get(1)[0];
        assertEquals(DataTaskStatus.CANCELED, patch.getStatus());
        assertNotNull(patch.getFinishTime());
        assertEquals(Boolean.FALSE, patch.getCancelRequested());
    }

    @Test
    public void testReapExpiredAndActiveChecks() {
        recorder.stub("failExpiredLeases", 5);
        assertEquals(5, dao.reapExpired("expired"));

        recorder.stub("selectCount", 2L, 0L);
        assertTrue(dao.hasActiveByDef(1L));
        assertFalse(dao.hasActiveByDef(2L));
    }

    @Test
    public void testSearchPassthrough() {
        DataTaskJobEntity entity = DataTaskJobEntity.builder().id(1L).build();
        recorder.stub("selectList", Collections.singletonList(entity), Collections.singletonList(entity));
        assertEquals(entity, dao.search(null, null).get(0));
        assertEquals(entity, dao.search(5L, DataTaskStatus.RUNNING).get(0));
        // status column carries EnumTypeHandler, so the wrapper must bind the enum itself,
        // never status.name(): MyBatis-Plus would route a String through EnumTypeHandler and fail
        QueryWrapper<?> wrapper = (QueryWrapper<?>) recorder.calls.get("selectList").get(1)[0];
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(DataTaskStatus.RUNNING));
    }
}
