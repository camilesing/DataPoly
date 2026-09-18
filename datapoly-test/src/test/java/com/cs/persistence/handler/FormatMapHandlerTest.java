// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.common.enums.DataTypeFormatEnum;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class FormatMapHandlerTest {

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
        Map<DataTypeFormatEnum, String> value = new HashMap<>();
        value.put(DataTypeFormatEnum.DATE, "yyyy/MM/dd");
        new FormatMapHandler().setNonNullParameter(ps, 1, value, null);
        assertEquals("{\"DATE\":\"yyyy/MM/dd\"}", captured.get());

        new FormatMapHandler().setNonNullParameter(ps, 1, null, null);
        assertNull(captured.get());
        new FormatMapHandler().setNonNullParameter(ps, 1, new HashMap<DataTypeFormatEnum, String>(), null);
        assertNull(captured.get());
    }

    @Test
    public void testGetNullableResultVariants() throws Exception {
        FormatMapHandler handler = new FormatMapHandler();
        Map<DataTypeFormatEnum, String> parsed =
                handler.getNullableResult(resultSet("{\"DATE\":\"yyyy/MM/dd\"}"), "col");
        assertEquals("yyyy/MM/dd", parsed.get(DataTypeFormatEnum.DATE));
        assertEquals("by index", "yyyy/MM/dd",
                handler.getNullableResult(resultSet("{\"DATE\":\"yyyy/MM/dd\"}"), 1)
                        .get(DataTypeFormatEnum.DATE));
        assertTrue(handler.getNullableResult(resultSet(null), "col").isEmpty());
        assertTrue(handler.getNullableResult(resultSet(""), "col").isEmpty());
    }
}
