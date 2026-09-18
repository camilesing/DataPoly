// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ParamMapHandlerTest {

    private ResultSet resultSet(String value) {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, m, a) -> {
                    if ("getString".equals(m.getName())) {
                        return value;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    @Test
    public void testSetNonNullParameterWritesJson() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, m, a) -> {
                    if ("setString".equals(m.getName())) {
                        captured.set((String) a[1]);
                    }
                    return null;
                });
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("k", 1);
        new ParamMapHandler().setNonNullParameter(ps, 1, value, null);
        assertEquals("{\"k\":1}", captured.get());

        new ParamMapHandler().setNonNullParameter(ps, 1, null, null);
        assertNull(captured.get());
        new ParamMapHandler().setNonNullParameter(ps, 1, new HashMap<String, Object>(), null);
        assertNull(captured.get());
    }

    @Test
    public void testGetNullableResultVariants() throws Exception {
        ParamMapHandler handler = new ParamMapHandler();
        Map<String, Object> parsed = handler.getNullableResult(resultSet("{\"k\":1}"), "col");
        assertEquals(1, parsed.get("k"));
        assertEquals(1, handler.getNullableResult(resultSet("{\"k\":1}"), 1).get("k"));
        assertTrue(handler.getNullableResult(resultSet(null), "col").isEmpty());
        assertTrue(handler.getNullableResult(resultSet(""), "col").isEmpty());
    }
}
