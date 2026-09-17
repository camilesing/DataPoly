// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.common.dto.ItemParam;
import com.cs.common.enums.ParamTypeEnum;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ListParamHandlerTest {

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
        ItemParam param = new ItemParam();
        param.setName("p");
        param.setType(ParamTypeEnum.STRING);
        new ListParamHandler().setNonNullParameter(ps, 1,
                new ArrayList<>(java.util.Collections.singletonList(param)), null);
        // exact JSON layout is Jackson-defined; assert on the parsed round-trip instead
        ItemParam written = com.cs.persistence.util.JsonUtils.toBeanList(captured.get(), ItemParam.class).get(0);
        assertEquals("p", written.getName());
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
        new ListParamHandler().setNonNullParameter(ps, 1, null, null);
        assertNull(captured.get());
        new ListParamHandler().setNonNullParameter(ps, 1, new ArrayList<ItemParam>(), null);
        assertNull(captured.get());
    }

    @Test
    public void testGetNullableResultVariants() throws Exception {
        ListParamHandler handler = new ListParamHandler();
        List<ItemParam> parsed = handler.getNullableResult(
                resultSet("[{\"name\":\"p\",\"type\":\"STRING\"}]", false), "col");
        assertEquals("p", parsed.get(0).getName());

        assertEquals("by index", "p",
                handler.getNullableResult(resultSet("[{\"name\":\"p\"}]", false), 1).get(0).getName());
        assertNull("sql null must map to null", handler.getNullableResult(resultSet(null, true), "col"));
        assertTrue("empty string maps to empty list",
                handler.getNullableResult(resultSet("", false), "col").isEmpty());
        assertTrue("non-null json null-string maps to empty list",
                handler.getNullableResult(resultSet(null, false), "col").isEmpty());
    }
}
