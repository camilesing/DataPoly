// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.mapper.DataTaskDefMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class DataTaskDefDaoTest {

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
    private DataTaskDefDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(DataTaskDefEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new DataTaskDefDao();
        DataTaskTestSupport.setField(dao, "dataTaskDefMapper", recorder.create(DataTaskDefMapper.class));
    }

    @SuppressWarnings("unchecked")
    private QueryWrapper<DataTaskDefEntity> wrapperOf(Object[] args) {
        return (QueryWrapper<DataTaskDefEntity>) args[0];
    }

    @Test
    public void getByIdAndInsertPassThrough() {
        DataTaskDefEntity entity = DataTaskDefEntity.builder().id(5L).name("t").build();
        recorder.stub("selectById", entity);
        assertSame(entity, dao.getById(5L));

        DataTaskDefEntity fresh = DataTaskDefEntity.builder().build();
        dao.insert(fresh);
        assertEquals(1, recorder.count("insert"));
        assertSame(fresh, recorder.argsOf("insert")[0]);
    }

    @Test
    public void getByNameBindsNameEquality() {
        DataTaskDefEntity entity = DataTaskDefEntity.builder().id(5L).name("unique").build();
        recorder.stub("selectOne", entity);
        assertSame(entity, dao.getByName("unique"));

        QueryWrapper<DataTaskDefEntity> wrapper = wrapperOf(recorder.argsOf("selectOne"));
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("unique"));
    }

    @Test
    public void updateAndDeleteByIdPassThrough() {
        DataTaskDefEntity patch = DataTaskDefEntity.builder().id(5L).build();
        dao.update(patch);
        assertEquals(1, recorder.count("updateById"));
        assertSame(patch, recorder.argsOf("updateById")[0]);

        dao.deleteById(5L);
        assertEquals(1, recorder.count("deleteById"));
        assertEquals(5L, recorder.argsOf("deleteById")[0]);
    }

    @Test
    public void searchAllWithoutTextKeepsTheFilterEmpty() {
        DataTaskDefEntity entity = DataTaskDefEntity.builder().build();
        for (String noText : new String[]{null, ""}) {
            recorder.stub("selectList", Collections.singletonList(entity));
            assertSame(entity, dao.searchAll(noText).get(0));

            QueryWrapper<DataTaskDefEntity> wrapper = wrapperOf(recorder.argsOf("selectList"));
            wrapper.getSqlSegment();
            assertTrue("no like parameters may leak in for empty text",
                    wrapper.getParamNameValuePairs().isEmpty());
        }
    }

    @Test
    public void searchWithTextLikesNameAndDescription() {
        DataTaskDefEntity entity = DataTaskDefEntity.builder().build();
        recorder.stub("selectList", Collections.singletonList(entity));
        assertSame(entity, dao.searchAll("nightly").get(0));

        QueryWrapper<DataTaskDefEntity> wrapper = wrapperOf(recorder.argsOf("selectList"));
        wrapper.getSqlSegment();
        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        assertEquals("name and description each bind one like value",
                2, values.size());
        // the dao pre-wraps the text and MyBatis-Plus like() wraps it again (%%…%% matches the
        // same rows as %…%, so the double wrap is harmless); lock the actual bound form
        for (Object value : values) {
            assertEquals("%%nightly%%", value);
        }
    }

    @Test
    public void existsDatasourceByIdReflectsCount() {
        recorder.stub("selectCount", 2L, 0L);
        assertTrue(dao.existsDatasourceById(7L));
        assertFalse(dao.existsDatasourceById(8L));

        QueryWrapper<DataTaskDefEntity> wrapper = wrapperOf(recorder.argsOf("selectCount"));
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
    }
}
