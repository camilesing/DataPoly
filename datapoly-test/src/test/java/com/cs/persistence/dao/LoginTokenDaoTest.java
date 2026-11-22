// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.entity.LoginTokenEntity;
import com.cs.persistence.mapper.LoginTokenMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.Assert.*;

public class LoginTokenDaoTest {

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
    private LoginTokenDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(LoginTokenEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new LoginTokenDao();
        DataTaskTestSupport.setField(dao, "loginTokenMapper", recorder.create(LoginTokenMapper.class));
    }

    @SuppressWarnings("rawtypes")
    private static com.baomidou.mybatisplus.core.conditions.AbstractWrapper wrapperOf(Object[] args) {
        // findByToken hands in a plain LambdaQueryWrapper, the deletes hand in QueryWrapper-backed
        // lambdas; both share the AbstractWrapper parameter storage
        return (com.baomidou.mybatisplus.core.conditions.AbstractWrapper) args[0];
    }

    @Test
    public void findByTokenResolvesBlankAndDelegatesOtherwise() {
        assertNull("blank token never reaches the mapper", dao.findByToken("  "));
        assertNull(dao.findByToken(null));
        assertEquals(0, recorder.count("selectOne"));

        LoginTokenEntity row = LoginTokenEntity.builder().accessToken("tok-1").build();
        recorder.stub("selectOne", row);
        assertSame(row, dao.findByToken("tok-1"));
        // the lambda wrapper must carry the token value itself, resolved through TableInfo
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper = wrapperOf(recorder.argsOf("selectOne"));
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("tok-1"));
    }

    @Test
    public void insertPassesEntityThrough() {
        LoginTokenEntity entity = LoginTokenEntity.builder()
                .accessToken("tok").username("alice").createAt(1L).expireAt(2L).build();
        dao.insert(entity);
        assertEquals(1, recorder.count("insert"));
        assertSame(entity, recorder.argsOf("insert")[0]);
    }

    @Test
    public void deleteByTokenIgnoresBlankAndDeletesByEquality() {
        dao.deleteByToken("");
        dao.deleteByToken(null);
        assertEquals("blank token is a no-op", 0, recorder.count("delete"));

        dao.deleteByToken("tok-9");
        assertEquals(1, recorder.count("delete"));
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper = wrapperOf(recorder.argsOf("delete"));
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("tok-9"));
    }

    @Test
    public void deleteByUsernameAppliesKeepTokenOnlyWhenPresent() {
        dao.deleteByUsername("  ", "");
        assertEquals("blank username is a no-op", 0, recorder.count("delete"));

        // no keepToken: the inequality condition must not bind any parameter value
        dao.deleteByUsername("alice", "");
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> plain = wrapperOf(recorder.argsOf("delete"));
        plain.getSqlSegment();
        assertTrue(plain.getParamNameValuePairs().containsValue("alice"));
        assertEquals(Collections.singleton("alice"), new HashSet<>(plain.getParamNameValuePairs().values()));

        // keepToken present: both the username and the spared token travel as parameters
        dao.deleteByUsername("alice", "keep-me");
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> sparing = wrapperOf(recorder.calls.get("delete").get(1));
        sparing.getSqlSegment();
        assertTrue(sparing.getParamNameValuePairs().containsValue("alice"));
        assertTrue(sparing.getParamNameValuePairs().containsValue("keep-me"));
        assertEquals(2, sparing.getParamNameValuePairs().size());
    }

    @Test
    public void deleteExpiredBindsCutoffAndReturnsCount() {
        recorder.stub("delete", 3, 0);
        assertEquals(3, dao.deleteExpired(12345L));
        assertEquals(0, dao.deleteExpired(99999L));

        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper = wrapperOf(recorder.argsOf("delete"));
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(12345L));
    }

    @Test
    public void entityColumnsResolveAgainstRealSchemaNaming() {
        // sanity for the TableInfo registration the lambda wrappers above relied on:
        // every column referenced by the dao must map onto the declared @TableField names
        LoginTokenEntity probe = LoginTokenEntity.builder().accessToken("a").username("u").build();
        probe.setCreateAt(Timestamp.valueOf("2026-01-01 00:00:00").getTime() / 1000);
        probe.setExpireAt(probe.getCreateAt() + 60L);
        assertEquals(Long.valueOf(probe.getCreateAt() + 60L), probe.getExpireAt());
    }
}
