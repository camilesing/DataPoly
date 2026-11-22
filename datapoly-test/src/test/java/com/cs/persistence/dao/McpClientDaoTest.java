// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.McpClientEntity;
import com.cs.persistence.mapper.McpClientMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * One-line mapper delegations; the two {@code exists*} probes hinge on
 * {@code selectOne} returning null vs a row, so both outcomes are pinned.
 */
public class McpClientDaoTest {

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
    private McpClientDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(McpClientEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new McpClientDao();
        DataTaskTestSupport.setField(dao, "mcpClientMapper", recorder.create(McpClientMapper.class));
    }

    @Test
    public void insertAndGetByIdPassThrough() {
        McpClientEntity entity = McpClientEntity.builder().name("reader").manage(Boolean.FALSE).build();
        dao.insert(entity);
        assertSame(entity, recorder.argsOf("insert")[0]);

        recorder.stub("selectById", entity);
        assertSame(entity, dao.getById(9L));
        assertEquals(Long.valueOf(9L), recorder.argsOf("selectById")[0]);
    }

    @Test
    public void listAllWithAndWithoutSearchText() {
        McpClientEntity entity = new McpClientEntity();
        recorder.stub("selectList", Collections.singletonList(entity), Collections.singletonList(entity));
        assertEquals(entity, dao.listAll("read").get(0));
        assertEquals(entity, dao.listAll(null).get(0));
        recorder.calls.get("selectList").forEach(call -> assertNotNull(call[0]));
    }

    @Test
    public void existsAccessTokenFollowsSelectOne() {
        recorder.stub("selectOne", (Object) null);
        assertFalse(dao.existsAccessToken("missing"));
        assertEquals(1, recorder.count("selectOne"));

        McpClientEntity entity = new McpClientEntity();
        recorder.stub("selectOne", entity);
        assertTrue(dao.existsAccessToken("dp_token"));
    }

    @Test
    public void existsManageAccessTokenFollowsSelectOne() {
        recorder.stub("selectOne", (Object) null);
        assertFalse(dao.existsManageAccessToken("missing"));

        McpClientEntity entity = new McpClientEntity();
        recorder.stub("selectOne", entity);
        assertTrue(dao.existsManageAccessToken("dp_manage_token"));
    }

    @Test
    public void updateAndDeleteDelegate() {
        McpClientEntity entity = McpClientEntity.builder().id(4L).name("renamed").build();
        dao.updateById(entity);
        assertSame(entity, recorder.argsOf("updateById")[0]);

        dao.deleteById(4L);
        assertEquals(Long.valueOf(4L), recorder.argsOf("deleteById")[0]);
    }
}
