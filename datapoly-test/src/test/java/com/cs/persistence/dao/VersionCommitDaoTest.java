// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.exception.CommonException;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.VersionCommitEntity;
import com.cs.persistence.mapper.VersionCommitMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class VersionCommitDaoTest {

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
    private VersionCommitDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(com.cs.persistence.entity.VersionCommitEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        recorder = new Recorder();
        dao = new VersionCommitDao();
        DataTaskTestSupport.setField(dao, "versionCommitMapper", recorder.create(VersionCommitMapper.class));
        // createVersion resolves itself through SpringUtil: stub the bean factory to return this dao
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length == 1
                            && a[0] == VersionCommitDao.class) {
                        return dao;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    if ("toString".equals(m.getName())) {
                        return "stubBeanFactory";
                    }
                    return null;
                });
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @After
    public void tearDown() throws Exception {
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    public void testDoCreateVersionStartsAtOne() {
        recorder.stub("getMaxVersion", (Object) null);
        VersionCommitEntity created = dao.doCreateVersion(5L, "desc", "content");
        assertEquals(Long.valueOf(5L), created.getBizId());
        assertEquals(Integer.valueOf(1), created.getVersion());
        assertEquals("desc", created.getDescription());
        assertEquals("content", created.getContent());
    }

    @Test
    public void testDoCreateVersionIncrementsMax() {
        recorder.stub("getMaxVersion", 7);
        assertEquals(Integer.valueOf(8), dao.doCreateVersion(5L, null, null).getVersion());
    }

    @Test
    public void testCreateVersionRetriesOnDuplicateKeyThenSucceeds() {
        recorder.stub("getMaxVersion", null, null, null);
        recorder.stub("insert",
                new DuplicateKeyException("dup"), new DuplicateKeyException("dup"), (Object) null);
        VersionCommitEntity created = dao.createVersion(5L, "desc", "content");
        assertEquals(Integer.valueOf(1), created.getVersion());
        assertEquals(3, recorder.count("insert"));
    }

    @Test
    public void testCreateVersionGivesUpAfterMaxAttempts() {
        recorder.stub("getMaxVersion", null, null, null);
        recorder.stub("insert",
                new DuplicateKeyException("dup"), new DuplicateKeyException("dup"), new DuplicateKeyException("dup"));
        try {
            dao.createVersion(5L, "desc", "content");
            fail("persistent conflicts must surface as CommonException");
        } catch (CommonException expected) {
            assertEquals(3, recorder.count("insert"));
        }
    }

    @Test
    public void testLookups() {
        VersionCommitEntity entity = VersionCommitEntity.builder().id(1L).build();
        recorder.stub("getLatestVersion", entity);
        assertSame(entity, dao.getLatestVersion(5L));

        recorder.stub("selectById", entity);
        assertSame(entity, dao.getByCommitId(1L));

        recorder.stub("selectList", Collections.singletonList(entity), Collections.singletonList(entity));
        assertEquals(entity, dao.getVersionList(5L, true).get(0));
        assertEquals(entity, dao.getVersionList(5L, false).get(0));
    }
}
