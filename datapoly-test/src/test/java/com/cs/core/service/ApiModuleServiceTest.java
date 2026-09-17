// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.exception.CommonException;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiModuleAssignments;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiModuleDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.entity.ApiModuleEntity;
import com.cs.persistence.entity.ModuleAssignmentEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class ApiModuleServiceTest {

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

    private Recorder moduleRecorder;
    private Recorder assignmentRecorder;
    private Recorder onlineRecorder;
    private ApiModuleService service;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.ApiAssignmentEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        moduleRecorder = new Recorder();
        assignmentRecorder = new Recorder();
        onlineRecorder = new Recorder();

        ApiModuleDao moduleDao = new ApiModuleDao();
        DataTaskTestSupport.setField(moduleDao, "apiModuleMapper",
                moduleRecorder.create(com.cs.persistence.mapper.ApiModuleMapper.class));
        ApiAssignmentDao assignmentDao = new ApiAssignmentDao();
        DataTaskTestSupport.setField(assignmentDao, "apiAssignmentMapper",
                assignmentRecorder.create(com.cs.persistence.mapper.ApiAssignmentMapper.class));

        service = new ApiModuleService();
        DataTaskTestSupport.setField(service, "apiModuleDao", moduleDao);
        DataTaskTestSupport.setField(service, "apiAssignmentDao", assignmentDao);

        ApiOnlineDao onlineDao = new ApiOnlineDao();
        DataTaskTestSupport.setField(onlineDao, "apiOnlineMapper",
                onlineRecorder.create(com.cs.persistence.mapper.ApiOnlineMapper.class));
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
        com.github.pagehelper.PageHelper.clearPage();
    }

    @After
    public void tearDown() throws Exception {
        com.github.pagehelper.PageHelper.clearPage();
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    public void testCreateAndUpdateModule() {
        service.createModule("m");
        assertEquals(1, moduleRecorder.count("insert"));

        moduleRecorder.stub("insert", new DuplicateKeyException("dup"));
        try {
            service.createModule("m");
            fail("duplicate module name must translate to CommonException");
        } catch (CommonException expected) {
        }

        ApiModuleEntity entity = ApiModuleEntity.builder().id(5L).name("old").build();
        moduleRecorder.stub("selectById", entity);
        service.updateModule(5L, "new");
        assertEquals("new", entity.getName());
        assertEquals(1, moduleRecorder.count("updateById"));

        moduleRecorder.stub("selectById", (Object) null);
        try {
            service.updateModule(6L, "new");
            fail("missing module must throw");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testDeleteModuleGuards() {
        assignmentRecorder.stub("selectCount", 1L);
        try {
            service.deleteModule(5L);
            fail("module in use by assignments must not be deletable");
        } catch (CommonException expected) {
        }
        assertEquals(0, moduleRecorder.count("deleteById"));

        assignmentRecorder.stub("selectCount", 0L);
        onlineRecorder.stub("selectCount", 3L);
        try {
            service.deleteModule(5L);
            fail("module in use by online APIs must not be deletable");
        } catch (CommonException expected) {
        }
        assertEquals(0, moduleRecorder.count("deleteById"));

        onlineRecorder.stub("selectCount", 0L);
        assignmentRecorder.stub("selectCount", 0L);
        service.deleteModule(5L);
        assertEquals(1, moduleRecorder.count("deleteById"));
    }

    @Test
    public void testListAllWrapsPageHelperPagination() {
        moduleRecorder.stub("selectList", Arrays.asList(ApiModuleEntity.builder().id(1L).build()));
        EntitySearchRequest request = new EntitySearchRequest();
        request.setPage(1);
        request.setSize(10);
        PageResult<ApiModuleEntity> result = service.listAll(request);
        assertEquals(1, result.getData().size());
        assertEquals(1, result.getPagination().getPage());
    }

    @Test
    public void testModuleTreeGroupsAssignmentsByModule() {
        ModuleAssignmentEntity first = new ModuleAssignmentEntity();
        first.setModuleId(2L);
        first.setModuleName("mod-2");
        first.setAssigmentId(10L);
        first.setAssigmentName("api-a");
        first.setGroupId(30L);
        ModuleAssignmentEntity second = new ModuleAssignmentEntity();
        second.setModuleId(2L);
        second.setModuleName("mod-2");
        second.setAssigmentId(11L);
        second.setAssigmentName("api-b");
        second.setGroupId(31L);

        assignmentRecorder.stub("getModuleAssignments", Arrays.asList(first, second));
        List<ApiModuleAssignments> tree = service.moduleTree(30L);
        assertEquals(1, tree.size());
        ApiModuleAssignments module = tree.get(0);
        assertEquals(Long.valueOf(2L), module.getId());
        assertEquals("mod-2", module.getName());
        assertEquals(2, module.getChildren().size());
        assertEquals(Boolean.TRUE, module.getChildren().get(0).getSelected());
        assertEquals(Boolean.FALSE, module.getChildren().get(1).getSelected());
        assertEquals("[11]api-b", module.getChildren().get(1).getName());
    }
}
