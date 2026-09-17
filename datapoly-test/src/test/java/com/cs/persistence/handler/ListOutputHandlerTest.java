// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.common.dto.OutParam;
import com.cs.common.enums.ParamTypeEnum;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ListOutputHandlerTest {

    private ResultSet resultSet(String value, boolean wasNull) {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, m, a) -> {
                    if ("getString".equals(m.getName())) {
                        return value;
                    }
                    if ("wasNull".equals(m.getName())) {
                        return wasNull;
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
        OutParam param = new OutParam();
        param.setName("o");
        param.setType(ParamTypeEnum.STRING);
        new ListOutputHandler().setNonNullParameter(ps, 1,
                new ArrayList<>(java.util.Collections.singletonList(param)), null);
        // exact JSON layout is Jackson-defined; assert on the parsed round-trip instead
        OutParam written = com.cs.persistence.util.JsonUtils.toBeanList(captured.get(), OutParam.class).get(0);
        assertEquals("o", written.getName());
        assertEquals(ParamTypeEnum.STRING, written.getType());
    }

    @Test
    public void testSetNonNullParameterNullAndEmptyLists() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, m, a) -> {
                    if ("setString".equals(m.getName())) {
                        captured.set((String) a[1]);
                    }
                    return null;
                });
        new ListOutputHandler().setNonNullParameter(ps, 1, null, null);
        assertNull(captured.get());
        new ListOutputHandler().setNonNullParameter(ps, 1, new ArrayList<OutParam>(), null);
        assertNull(captured.get());
    }

    @Test
    public void testGetNullableResultVariants() throws Exception {
        ListOutputHandler handler = new ListOutputHandler();
        assertEquals("o", handler.getNullableResult(
                resultSet("[{\"name\":\"o\",\"type\":\"STRING\"}]", false), "col").get(0).getName());
        assertEquals("by index", "o",
                handler.getNullableResult(resultSet("[{\"name\":\"o\"}]", false), 1).get(0).getName());
        assertNull(handler.getNullableResult(resultSet(null, true), "col"));
        assertTrue(handler.getNullableResult(resultSet("", false), "col").isEmpty());
    }
}
