// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class StringMapHandlerTest {

    @SuppressWarnings("unchecked")
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
        Map<String, String> value = new LinkedHashMap<>();
        value.put("k", "v");
        new StringMapHandler().setNonNullParameter(ps, 1, value, null);
        assertEquals("{\"k\":\"v\"}", captured.get());
    }

    @Test
    public void testGetNullableResultVariants() throws Exception {
        StringMapHandler handler = new StringMapHandler();
        Map<String, String> parsed = handler.getNullableResult(resultSet("{\"k\":\"v\"}"), "col");
        assertEquals("v", parsed.get("k"));

        Map<String, String> byIndex = handler.getNullableResult(resultSet("{\"k\":\"v2\"}"), 1);
        assertEquals("v2", byIndex.get("k"));

        assertEquals(0, handler.getNullableResult(resultSet(null), "col").size());
        assertEquals(0, handler.getNullableResult(resultSet(""), "col").size());

        CallableStatement cs = (CallableStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CallableStatement.class},
                (p, m, a) -> "getString".equals(m.getName()) ? "{\"k\":\"v3\"}" : null);
        assertEquals("v3", handler.getNullableResult(cs, 1).get("k"));
    }
}
