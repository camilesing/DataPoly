// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.common.dto.IdWithName;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.AppClientEntity;
import com.cs.persistence.entity.ClientGroupEntity;
import com.cs.persistence.mapper.AppClientMapper;
import com.cs.persistence.mapper.ClientGroupMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class AppClientDaoTest {

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

    private Recorder appClientRecorder;
    private Recorder clientGroupRecorder;
    private AppClientDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.AppClientEntity.class, com.cs.persistence.entity.ClientGroupEntity.class);
    }

    @Before
    public void setUp() {
        appClientRecorder = new Recorder();
        clientGroupRecorder = new Recorder();
        dao = new AppClientDao();
        DataTaskTestSupport.setField(dao, "appClientMapper", appClientRecorder.create(AppClientMapper.class));
        DataTaskTestSupport.setField(dao, "clientAuthMapper", clientGroupRecorder.create(ClientGroupMapper.class));
    }

    @Test
    public void testListAllVariants() {
        AppClientEntity entity = new AppClientEntity();
        appClientRecorder.stub("selectList", Collections.singletonList(entity), Collections.singletonList(entity));
        assertEquals(entity, dao.listAll().get(0));
        assertEquals(entity, dao.listAll("text").get(0));

        appClientRecorder.stub("searchAppClient", Collections.singletonList(entity));
        assertEquals(entity, dao.listAll("text", 5L).get(0));
        assertEquals("%text%", appClientRecorder.argsOf("searchAppClient")[0]);
        assertEquals(5L, appClientRecorder.argsOf("searchAppClient")[1]);

        appClientRecorder.stub("searchAppClient", Collections.singletonList(entity));
        assertEquals(entity, dao.listAll(null, 5L).get(0));
        assertNull(appClientRecorder.calls.get("searchAppClient").get(1)[0]);
    }

    @Test
    public void testLookups() {
        AppClientEntity entity = new AppClientEntity();
        entity.setId(9L);
        appClientRecorder.stub("selectById", entity);
        assertSame(entity, dao.getById(9L));

        appClientRecorder.stub("selectOne", entity, entity, (Object) null);
        assertSame(entity, dao.getByAppKey("key"));
        assertSame(entity, dao.getByAccessToken("token"));
        assertNull(dao.getByAccessToken("missing"));

        appClientRecorder.stub("selectList", Collections.singletonList(entity));
        assertEquals(entity, dao.getByName("name").get(0));
    }

    @Test
    public void testExistsAuthGroupsRequiresClient() {
        appClientRecorder.stub("selectOne", (Object) null);
        assertFalse(dao.existsAuthGroups("key", 5L));
        assertEquals("unknown app key must short-circuit", 0, clientGroupRecorder.count("selectCount"));

        AppClientEntity entity = new AppClientEntity();
        entity.setId(9L);
        appClientRecorder.stub("selectOne", entity);
        clientGroupRecorder.stub("selectCount", 1L, 0L);
        assertTrue(dao.existsAuthGroups("key", 5L));
        assertFalse(dao.existsAuthGroups("key", 6L));
    }

    @Test
    public void testTokenUpdateAndClear() {
        dao.updateTokenByAppKey("key", "new-token");
        AppClientEntity patch = (AppClientEntity) appClientRecorder.argsOf("update")[0];
        assertEquals("key", patch.getAppKey());
        assertEquals("new-token", patch.getAccessToken());

        dao.clearTokenByAppKey("key");
        assertNull(appClientRecorder.calls.get("update").get(1)[0]);
    }

    @Test
    public void testDeleteByIdRemovesAuthRows() {
        dao.deleteById(9L);
        assertEquals(1, appClientRecorder.count("deleteById"));
        assertEquals(1, clientGroupRecorder.count("delete"));
    }

    @Test
    public void testDeleteClientAuthByGroupId() {
        dao.deleteClientAuthByGroupId(5L);
        assertEquals(1, clientGroupRecorder.count("delete"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSaveAuthGroupDeduplicatesAndSetsClient() {
        dao.saveAuthGroup(5L, Arrays.asList(1L, 2L, 2L, 3L));
        assertEquals(1, clientGroupRecorder.count("delete"));
        List<ClientGroupEntity> inserted =
                (List<ClientGroupEntity>) clientGroupRecorder.argsOf("insertList")[0];
        assertEquals(3, inserted.size());
        for (ClientGroupEntity row : inserted) {
            assertEquals(Long.valueOf(5L), row.getClientId());
        }
        assertEquals(Long.valueOf(1L), inserted.get(0).getGroupId());
        assertEquals(Long.valueOf(2L), inserted.get(1).getGroupId());
        assertEquals(Long.valueOf(3L), inserted.get(2).getGroupId());
    }

    @Test
    public void testSaveAuthGroupWithoutGroupsOnlyDeletes() {
        dao.saveAuthGroup(5L, Collections.<Long>emptyList());
        dao.saveAuthGroup(5L, null);
        assertEquals(2, clientGroupRecorder.count("delete"));
        assertEquals(0, clientGroupRecorder.count("insertList"));
    }

    @Test
    public void testGetGroupAuthPassthrough() {
        IdWithName expected = new IdWithName();
        clientGroupRecorder.stub("getGroupAuth", Collections.singletonList(expected));
        assertSame(expected, dao.getGroupAuth(9L).get(0));
    }
}
