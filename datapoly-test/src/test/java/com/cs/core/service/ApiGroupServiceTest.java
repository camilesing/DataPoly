// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.exception.CommonException;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiGroupDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.ApiGroupEntity;
import com.cs.persistence.mapper.ApiGroupMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class ApiGroupServiceTest {

    private static class Recorder {
        final Map<String, List<Object[]>> calls = new LinkedHashMap<>();
        private final Map<String, Queue<Object>> stubs = new HashMap<>();

        @SuppressWarnings("unchecked")
        <M> M create(Class<M> type) {
            return (M) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, m, a) -> {
                        calls.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(a);
                        Queue<Object> queue = stubs.get(m.getName());
                        if (queue != null && !queue.isEmpty()) {
                            Object result = queue.poll();
                            if (result instanceof RuntimeException) {
                                throw (RuntimeException) result;
                            }
                            return result;
                        }
                        Class<?> rt = m.getReturnType();
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

        int count(String method) {
            return calls.getOrDefault(method, Collections.emptyList()).size();
        }
    }

    private Recorder groupRecorder;
    private Recorder assignmentRecorder;
    private Recorder onlineRecorder;
    private Recorder clientRecorder;
    private ApiGroupService service;

    @org.junit.BeforeClass
    public static void initEntityMetadata() {
        com.cs.persistence.PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.ApiGroupEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        groupRecorder = new Recorder();
        assignmentRecorder = new Recorder();
        onlineRecorder = new Recorder();
        clientRecorder = new Recorder();

        ApiAssignmentDao assignmentDao = new ApiAssignmentDao();
        DataTaskTestSupport.setField(assignmentDao, "apiAssignmentMapper", assignmentRecorder.create(
                com.cs.persistence.mapper.ApiAssignmentMapper.class));
        ApiOnlineDao onlineDao = new ApiOnlineDao();
        DataTaskTestSupport.setField(onlineDao, "apiOnlineMapper",
                onlineRecorder.create(com.cs.persistence.mapper.ApiOnlineMapper.class));
        AppClientDao appClientDao = new AppClientDao();
        DataTaskTestSupport.setField(appClientDao, "appClientMapper",
                clientRecorder.create(com.cs.persistence.mapper.AppClientMapper.class));
        DataTaskTestSupport.setField(appClientDao, "clientAuthMapper",
                clientRecorder.create(com.cs.persistence.mapper.ClientGroupMapper.class));

        service = new ApiGroupService();
        ApiGroupDao groupDao = new ApiGroupDao();
        DataTaskTestSupport.setField(groupDao, "apiGroupMapper", groupRecorder.create(ApiGroupMapper.class));
        DataTaskTestSupport.setField(service, "apiGroupDao", groupDao);
        DataTaskTestSupport.setField(service, "apiAssignmentDao", assignmentDao);
        DataTaskTestSupport.setField(service, "appClientDao", appClientDao);

        installOnlineDao(onlineDao);
        com.github.pagehelper.PageHelper.clearPage();
    }

    @After
    public void tearDown() throws Exception {
        com.github.pagehelper.PageHelper.clearPage();
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    private void installOnlineDao(final ApiOnlineDao onlineDao) throws Exception {
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length == 1
                            && a[0] == ApiOnlineDao.class) {
                        return onlineDao;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @Test
    public void testCreateGroupTranslatesDuplicateKey() {
        service.createGroup("g");
        assertEquals(1, groupRecorder.count("insert"));

        groupRecorder.stub("insert", new DuplicateKeyException("dup"));
        try {
            service.createGroup("g");
            fail("duplicate name must translate to CommonException");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testUpdateGroupRequiresExistingRow() {
        groupRecorder.stub("selectById", (Object) null);
        try {
            service.updateGroup(5L, "new-name");
            fail("missing group must throw");
        } catch (CommonException expected) {
        }

        ApiGroupEntity entity = ApiGroupEntity.builder().id(5L).name("old").build();
        groupRecorder.stub("selectById", entity);
        service.updateGroup(5L, "new-name");
        assertEquals("new-name", entity.getName());
        assertEquals(1, groupRecorder.count("updateById"));
    }

    @Test
    public void testDeleteGroupGuards() {
        try {
            service.deleteGroup(1L);
            fail("default group must not be deletable");
        } catch (CommonException expected) {
        }

        assignmentRecorder.stub("selectCount", 1L);
        try {
            service.deleteGroup(5L);
            fail("group in use by assignments must not be deletable");
        } catch (CommonException expected) {
        }
        assertEquals(0, groupRecorder.count("deleteById"));

        assignmentRecorder.stub("selectCount", 0L);
        onlineRecorder.stub("selectCount", 2L);
        try {
            service.deleteGroup(5L);
            fail("group in use by online APIs must not be deletable");
        } catch (CommonException expected) {
        }
        assertEquals(0, groupRecorder.count("deleteById"));
    }

    @Test
    public void testDeleteGroupHappyPathCascadesClientAuth() {
        assignmentRecorder.stub("selectCount", 0L);
        onlineRecorder.stub("selectCount", 0L);
        service.deleteGroup(5L);
        assertEquals(1, groupRecorder.count("deleteById"));
        assertEquals(1, clientRecorder.count("delete"));
    }

    @Test
    public void testListAllWrapsPageHelperPagination() {
        groupRecorder.stub("selectList", Arrays.asList(ApiGroupEntity.builder().id(1L).build()));
        EntitySearchRequest request = new EntitySearchRequest();
        request.setPage(1);
        request.setSize(10);
        PageResult<ApiGroupEntity> result = service.listAll(request);
        assertEquals(1, result.getData().size());
        assertEquals(1, result.getPagination().getPage());
        assertEquals(10, result.getPagination().getSize());
    }
}
