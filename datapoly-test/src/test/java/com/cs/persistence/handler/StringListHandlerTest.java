// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class StringListHandlerTest {

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
        new StringListHandler().setNonNullParameter(ps, 1, Arrays.asList("a", "b"), null);
        assertEquals("[\"a\",\"b\"]", captured.get());
    }

    @Test
    public void testGetNullableResultVariants() throws Exception {
        StringListHandler handler = new StringListHandler();
        assertEquals(Arrays.asList("a", "b"), handler.getNullableResult(resultSet("[\"a\",\"b\"]"), "col"));
        assertEquals(Arrays.asList("a"), handler.getNullableResult(resultSet("[\"a\"]"), 1));
        assertTrue(handler.getNullableResult(resultSet(null), "col").isEmpty());
        assertTrue(handler.getNullableResult(resultSet(""), "col").isEmpty());

        CallableStatement cs = (CallableStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CallableStatement.class},
                (p, m, a) -> "getString".equals(m.getName()) ? "[\"c\"]" : null);
        assertEquals(Arrays.asList("c"), handler.getNullableResult(cs, 1));
    }
}
