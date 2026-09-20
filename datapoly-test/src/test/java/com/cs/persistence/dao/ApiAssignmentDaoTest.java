// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiContextEntity;
import com.cs.persistence.entity.ModuleAssignmentEntity;
import com.cs.persistence.mapper.ApiAssignmentMapper;
import com.cs.persistence.mapper.ApiContextMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class ApiAssignmentDaoTest {

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

    private Recorder assignmentRecorder;
    private Recorder contextRecorder;
    private ApiAssignmentDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.ApiAssignmentEntity.class, com.cs.persistence.entity.ApiContextEntity.class);
    }

    @Before
    public void setUp() {
        assignmentRecorder = new Recorder();
        contextRecorder = new Recorder();
        ApiContextDao apiContextDao = new ApiContextDao();
        DataTaskTestSupport.setField(apiContextDao, "apiContextMapper",
                contextRecorder.create(ApiContextMapper.class));
        dao = new ApiAssignmentDao();
        DataTaskTestSupport.setField(dao, "apiAssignmentMapper",
                assignmentRecorder.create(ApiAssignmentMapper.class));
        DataTaskTestSupport.setField(dao, "apiContextDao", apiContextDao);
    }

    private ApiContextEntity context(Long id, Long apiId) {
        return ApiContextEntity.builder().id(id).apiId(apiId).build();
    }

    @Test
    public void testInsertRewiresContextRows() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder().id(77L).build();
        entity.setContextList(new ArrayList<>(Collections.singletonList(context(1L, 999L))));
        dao.insert(entity);
        assertEquals(1, assignmentRecorder.count("insert"));
        ApiContextEntity inserted = (ApiContextEntity) contextRecorder.argsOf("insert")[0];
        assertNull("context ids must be reset for reinsert", inserted.getId());
        assertEquals(Long.valueOf(77L), inserted.getApiId());
    }

    @Test
    public void testInsertWithoutContextsSkipsBatch() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder().id(1L).build();
        dao.insert(entity);
        assertEquals(0, contextRecorder.count("insert"));
    }

    @Test
    public void testUpdateReplacesContexts() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder().id(77L).build();
        entity.setContextList(new ArrayList<>(Collections.singletonList(context(1L, 999L))));
        dao.update(entity);
        assertEquals(1, assignmentRecorder.count("updateById"));
        assertEquals(1, contextRecorder.count("delete"));
        ApiContextEntity inserted = (ApiContextEntity) contextRecorder.argsOf("insert")[0];
        assertNull(inserted.getId());
        assertEquals(Long.valueOf(77L), inserted.getApiId());
    }

    @Test
    public void testGetByIdWithAndWithoutSql() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder().id(3L).build();
        assignmentRecorder.stub("selectById", entity, entity);
        contextRecorder.stub("selectList",
                Collections.singletonList(context(1L, 3L)), Collections.<ApiContextEntity>emptyList());

        ApiAssignmentEntity withSql = dao.getById(3L, true);
        assertEquals(1, withSql.getContextList().size());

        dao.getById(3L, false);
        assertEquals("second lookup must not query contexts again", 1, contextRecorder.count("selectList"));
    }

    @Test
    public void testGetByIdsAndModuleAssignments() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder().id(1L).build();
        assignmentRecorder.stub("selectBatchIds", Collections.singletonList(entity));
        assertEquals(entity, dao.getByIds(Collections.singletonList(1L)).get(0));

        ModuleAssignmentEntity moduleAssignment = new ModuleAssignmentEntity();
        assignmentRecorder.stub("getModuleAssignments", Collections.singletonList(moduleAssignment));
        assertSame(moduleAssignment, dao.getModuleAssignments().get(0));
    }

    @Test
    public void testGetByUkLoadsContexts() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder().id(3L).build();
        assignmentRecorder.stub("selectOne", entity, (Object) null);
        contextRecorder.stub("selectList", Collections.singletonList(context(1L, 3L)));

        assertEquals(1, dao.getByUk(HttpMethodEnum.GET, "/p").getContextList().size());
        assertNull(dao.getByUk(HttpMethodEnum.POST, "/missing"));
        // method column carries EnumTypeHandler, so the wrapper must bind the enum itself,
        // never method.name(): MyBatis-Plus would route a String through EnumTypeHandler and fail
        QueryWrapper<?> wrapper = (QueryWrapper<?>) assignmentRecorder.argsOf("selectOne")[0];
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(HttpMethodEnum.GET));
    }

    @Test
    public void testSearchAllWrapsSearchText() {
        assignmentRecorder.stub("searchAll", Collections.<ApiAssignmentEntity>emptyList());
        dao.searchAll(1L, 2L, Boolean.TRUE, "text", Boolean.FALSE);
        Object[] args = assignmentRecorder.argsOf("searchAll");
        assertEquals(1L, args[0]);
        assertEquals(2L, args[1]);
        assertEquals(Boolean.TRUE, args[2]);
        assertEquals("%text%", args[3]);
        assertEquals(Boolean.FALSE, args[4]);

        dao.searchAll(null, null, null, "", null);
        assertEquals("blank search text is passed through unchanged", "", assignmentRecorder.calls.get("searchAll").get(1)[3]);
    }

    @Test
    public void testListAllAndExistChecks() {
        assignmentRecorder.stub("selectList", Collections.<ApiAssignmentEntity>emptyList());
        assertTrue(dao.listAll().isEmpty());

        assignmentRecorder.stub("selectCount", 2L, 0L, 2L, 0L, 2L, 0L);
        assertTrue(dao.existsDataSourceById(1L));
        assertFalse(dao.existsDataSourceById(2L));
        assertTrue(dao.existsGroupById(1L));
        assertFalse(dao.existsGroupById(2L));
        assertTrue(dao.existsModuleById(1L));
        assertFalse(dao.existsModuleById(2L));
    }

    @Test
    public void testDeleteByIdAndGroupResets() {
        dao.deleteById(5L);
        assertEquals(1, contextRecorder.count("delete"));
        assertEquals(1, assignmentRecorder.count("deleteById"));

        dao.resetGroupByGroupId(5L);
        assertEquals(1, assignmentRecorder.count("resetGroup"));

        dao.updateGroup(5L, Arrays.asList(1L, 2L));
        assertEquals(1, assignmentRecorder.count("updateGroup"));
        dao.updateGroup(5L, Collections.<Long>emptyList());
        dao.updateGroup(5L, null);
        assertEquals(1, assignmentRecorder.count("updateGroup"));
    }

    @Test
    public void testUpgradeAssignmentDelegation() {
        assignmentRecorder.stub("getUpgradeOnlineAssignments", Collections.singletonList(9L));
        assertEquals(Long.valueOf(9L), dao.getUpgradeOnlineAssignments().get(0));

        dao.resetUpgradeOnlineAssignments(Collections.singletonList(9L));
        assertEquals(1, assignmentRecorder.count("resetUpgradeOnlineAssignments"));
        dao.resetUpgradeOnlineAssignments(Collections.<Long>emptyList());
        dao.resetUpgradeOnlineAssignments(null);
        assertEquals(1, assignmentRecorder.count("resetUpgradeOnlineAssignments"));
    }
}
