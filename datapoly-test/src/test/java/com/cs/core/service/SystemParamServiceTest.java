// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class SystemParamServiceTest {

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

    private Recorder recorder;
    private SystemParamService service;

    private SystemParamEntity param(ParamTypeEnum type, String value) {
        return SystemParamEntity.builder().paramType(type).paramValue(value).build();
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        SystemParamDao systemParamDao = new SystemParamDao();
        DataTaskTestSupport.setField(systemParamDao, "systemParamMapper",
                recorder.create(com.cs.persistence.mapper.SystemParamMapper.class));
        service = new SystemParamService();
        DataTaskTestSupport.setField(service, "systemParamDao", systemParamDao);
    }

    @Test
    public void testGetByParamKeyConvertsValue() {
        recorder.stub("selectOne", param(ParamTypeEnum.LONG, "42"), param(ParamTypeEnum.BOOLEAN, "true"));
        assertEquals(Long.valueOf(42L), service.getByParamKey("k1"));
        assertEquals(Boolean.TRUE, service.getByParamKey("k2"));
    }

    @Test
    public void testGetByParamKeyMissingThrows() {
        recorder.stub("selectOne", (Object) null);
        try {
            service.getByParamKey("missing");
            fail("missing key must throw");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testUpdateByParamKeyConvertsAndPersists() {
        // service lookup + dao re-lookup both resolve via selectOne
        recorder.stub("selectOne",
                param(ParamTypeEnum.BOOLEAN, "false"), param(ParamTypeEnum.BOOLEAN, "false"));
        service.updateByParamKey("k", "true");
        assertEquals(1, recorder.count("updateById"));
        SystemParamEntity updated = (SystemParamEntity) recorder.calls.get("updateById").get(0)[0];
        assertEquals("true", updated.getParamValue());
    }

    @Test
    public void testUpdateByParamKeyRejectsBlankConvertibleValue() {
        recorder.stub("selectOne", param(ParamTypeEnum.LONG, "1"), (Object) null);
        try {
            service.updateByParamKey("k", " ");
            fail("blank numeric value must be rejected");
        } catch (CommonException expected) {
        }
        try {
            service.updateByParamKey("k", "1");
            fail("missing key must throw on update");
        } catch (CommonException expected) {
        }
        assertEquals(0, recorder.count("updateById"));
    }

    @Test
    public void testGetIntByParamKeyDefaults() {
        recorder.stub("selectOne", (Object) null, param(ParamTypeEnum.LONG, "9"),
                param(ParamTypeEnum.LONG, "not-a-number"), param(ParamTypeEnum.STRING, "9"));
        assertEquals(7, service.getIntByParamKey("missing", 7).intValue());
        assertEquals(9, service.getIntByParamKey("k", 7).intValue());
        assertEquals("unparsable value falls back to default", 7, service.getIntByParamKey("k", 7).intValue());
        assertEquals("non-LONG typed params fall back to default", 7, service.getIntByParamKey("k", 7).intValue());
    }
}
