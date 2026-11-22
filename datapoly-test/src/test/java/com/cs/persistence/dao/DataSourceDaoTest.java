// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.mapper.DataSourceMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Each dao method is a one-line mapper delegation (plus lambda-wrapper construction, which
 * needs {@link PersistenceTestSupport#initTableInfo} to resolve column names without a mapper parse).
 */
public class DataSourceDaoTest {

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
    private DataSourceDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(DataSourceEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new DataSourceDao();
        DataTaskTestSupport.setField(dao, "dataSourceMapper", recorder.create(DataSourceMapper.class));
    }

    @Test
    public void insertPassesEntityThrough() {
        DataSourceEntity entity = DataSourceEntity.builder().name("mysql-prod").build();
        dao.insert(entity);
        assertSame(entity, recorder.argsOf("insert")[0]);
    }

    @Test
    public void getByIdReturnsMapperRow() {
        DataSourceEntity entity = new DataSourceEntity();
        recorder.stub("selectById", entity);
        assertSame(entity, dao.getById(5L));
        assertEquals(Long.valueOf(5L), recorder.argsOf("selectById")[0]);

        recorder.stub("selectById", (Object) null);
        assertNull(dao.getById(6L));
    }

    @Test
    public void getByNameQueriesByNameColumn() {
        DataSourceEntity entity = new DataSourceEntity();
        recorder.stub("selectOne", entity);
        assertSame(entity, dao.getByName("mysql-prod"));
    }

    @Test
    public void listAllWithSearchTextBuildsLikeFilter() {
        DataSourceEntity entity = new DataSourceEntity();
        recorder.stub("selectList", Collections.singletonList(entity), Collections.singletonList(entity));
        assertEquals(entity, dao.listAll("prod").get(0));
        // no-arg and blank text both mean "no filter" — same mapper call shape
        assertEquals(entity, dao.listAll().get(0));
        recorder.calls.get("selectList").forEach(call -> assertNotNull(call[0]));
    }

    @Test
    public void getAllIdListCollectsIdsIntoASet() {
        DataSourceEntity first = DataSourceEntity.builder().id(1L).build();
        DataSourceEntity second = DataSourceEntity.builder().id(2L).build();
        DataSourceEntity duplicate = DataSourceEntity.builder().id(1L).build();
        recorder.stub("selectList", Arrays.asList(first, second, duplicate));
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), dao.getAllIdList());
    }

    @Test
    public void updateAndDeleteDelegateById() {
        DataSourceEntity entity = DataSourceEntity.builder().id(3L).name("renamed").build();
        dao.updateById(entity);
        assertSame(entity, recorder.argsOf("updateById")[0]);

        dao.deleteById(3L);
        assertEquals(Long.valueOf(3L), recorder.argsOf("deleteById")[0]);
    }

    @Test
    public void totalCountNarrowsMapperLong() {
        recorder.stub("selectCount", 7L);
        assertEquals(7, dao.getTotalCount());
        assertNull(recorder.argsOf("selectCount")[0]);
    }
}
