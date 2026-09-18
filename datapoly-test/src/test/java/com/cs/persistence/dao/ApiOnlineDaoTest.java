// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.common.enums.HttpMethodEnum;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.common.dto.ApiIdVersion;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiOnlineEntity;
import com.cs.persistence.mapper.ApiOnlineMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class ApiOnlineDaoTest {

    /** Recording fake mapper: captures invocations, replays stubbed results. */
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
    }

    private Recorder recorder;
    private ApiOnlineDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.ApiOnlineEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new ApiOnlineDao();
        DataTaskTestSupport.setField(dao, "apiOnlineMapper", recorder.create(ApiOnlineMapper.class));
    }

    private ApiOnlineEntity onlineEntity(Long id, String content) {
        ApiOnlineEntity entity = new ApiOnlineEntity();
        entity.setId(id);
        entity.setContent(content);
        entity.setGroupId(11L);
        entity.setModuleId(22L);
        entity.setDatasourceId(33L);
        entity.setOpen(Boolean.TRUE);
        entity.setFlowStatus(Boolean.FALSE);
        entity.setCommitId(44L);
        return entity;
    }

    @Test
    public void testGetByApiIdMapsContentAndColumns() {
        recorder.stub("selectOne", onlineEntity(1L, "{\"name\":\"from-json\",\"path\":\"/p\"}"));
        ApiAssignmentEntity mapped = dao.getByApiId(7L);
        assertEquals("from-json", mapped.getName());
        assertEquals(Long.valueOf(11L), mapped.getGroupId());
        assertEquals(Long.valueOf(22L), mapped.getModuleId());
        assertEquals(Long.valueOf(33L), mapped.getDatasourceId());
        assertEquals(Boolean.TRUE, mapped.getOpen());
        assertEquals(Boolean.FALSE, mapped.getFlowStatus());
        assertEquals(Long.valueOf(44L), mapped.getCommitId());
    }

    @Test
    public void testGetByApiIdNullWhenNoRow() {
        recorder.stub("selectOne", (Object) null);
        assertNull(dao.getByApiId(7L));
    }

    @Test
    public void testFilterOnlineVariants() {
        assertTrue(dao.filterOnline(Collections.<Long>emptyList()).isEmpty());
        assertEquals(0, recorder.count("filterOnline"));

        ApiIdVersion idVersion = new ApiIdVersion();
        recorder.stub("filterOnline", Collections.singletonList(idVersion));
        assertEquals(idVersion, dao.filterOnline(Arrays.asList(1L, 2L)).get(0));

        recorder.stub("filterOnline", Collections.singletonList(idVersion));
        assertEquals(idVersion, dao.filterOnline(5L));

        recorder.stub("filterOnline", Collections.<ApiIdVersion>emptyList());
        assertNull(dao.filterOnline(6L));
    }

    @Test
    public void testListAndSearchAllMapEveryRow() {
        recorder.stub("selectList",
                Arrays.asList(onlineEntity(1L, "{\"name\":\"a\"}"), onlineEntity(2L, "{\"name\":\"b\"}")));
        List<ApiAssignmentEntity> all = dao.listAll();
        assertEquals(2, all.size());
        assertEquals("a", all.get(0).getName());

        recorder.stub("selectList", Collections.singletonList(onlineEntity(3L, "{\"name\":\"c\"}")));
        assertEquals(1, dao.searchAll(null, null, Boolean.TRUE, "c").size());
    }

    @Test
    public void testGetByUkAndCommitId() {
        recorder.stub("selectOne", onlineEntity(1L, "{\"name\":\"a\"}"));
        assertNotNull(dao.getByUk(HttpMethodEnum.GET, "/p"));
        assertNull(dao.getByUk(HttpMethodEnum.POST, "/missing"));

        recorder.stub("selectOne", onlineEntity(2L, "{}"));
        assertEquals(Long.valueOf(44L), dao.getCommitIdByUk(HttpMethodEnum.GET, "/p"));
        assertNull(dao.getCommitIdByUk(HttpMethodEnum.POST, "/missing"));
    }

    @Test
    public void testExistsByUniqueKey() {
        recorder.stub("selectOne", onlineEntity(9L, "{}"));
        assertTrue(dao.existsByUniqueKey(HttpMethodEnum.GET, "/p"));

        recorder.stub("selectOne", (Object) null);
        assertFalse(dao.existsByUniqueKey(HttpMethodEnum.GET, "/p"));
    }

    @Test
    public void testExistsByForeignKeys() {
        recorder.stub("selectCount", 3L, 0L, 3L, 0L, 3L, 0L);
        assertTrue(dao.existsDataSourceById(1L));
        assertFalse(dao.existsDataSourceById(2L));
        assertTrue(dao.existsGroupById(1L));
        assertFalse(dao.existsGroupById(2L));
        assertTrue(dao.existsModuleById(1L));
        assertFalse(dao.existsModuleById(2L));
    }

    @Test
    public void testFlowControlListPassthrough() {
        recorder.stub("selectList", Collections.singletonList(onlineEntity(1L, "{\"name\":\"f\"}")));
        assertEquals("f", dao.listFlowControlAll().get(0).getName());
    }

    @Test
    public void testMutatorsDelegateToMapper() {
        dao.upsert(onlineEntity(1L, "{}"));
        assertEquals(1, recorder.count("upsert"));

        dao.deleteByApiId(8L);
        assertEquals(1, recorder.count("delete"));

        dao.resetGroupByGroupId(5L);
        assertEquals(1, recorder.count("resetGroup"));

        dao.updateGroup(5L, Arrays.asList(1L, 2L));
        assertEquals(1, recorder.count("updateGroup"));

        dao.updateGroup(5L, Collections.<Long>emptyList());
        dao.updateGroup(5L, null);
        assertEquals("empty/null ids must not reach the mapper", 1, recorder.count("updateGroup"));
    }
}
