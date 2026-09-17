// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.persistence.entity.PoolConfig;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PoolConfigHandlerTest {

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
        PoolConfig config = PoolConfig.builder().maximumPoolSize(7).minimumIdle(3).build();
        new PoolConfigHandler().setNonNullParameter(ps, 1, config, null);
        PoolConfig written = com.cs.persistence.util.JsonUtils.toBeanObject(captured.get(), PoolConfig.class);
        assertEquals(Integer.valueOf(7), written.getMaximumPoolSize());
        assertEquals(Integer.valueOf(3), written.getMinimumIdle());
    }

    @Test
    public void testGetNullableResultDefaultsAndParses() throws Exception {
        PoolConfigHandler handler = new PoolConfigHandler();
        assertEquals(PoolConfig.DEFAULT.getMaximumPoolSize(),
                handler.getNullableResult(resultSet(null), "col").getMaximumPoolSize());
        assertEquals(PoolConfig.DEFAULT.getMaximumPoolSize(),
                handler.getNullableResult(resultSet(""), 1).getMaximumPoolSize());
        PoolConfig parsed = handler.getNullableResult(
                resultSet("{\"maximumPoolSize\":9,\"minimumIdle\":4}"), "col");
        assertEquals(Integer.valueOf(9), parsed.getMaximumPoolSize());
        assertEquals(Integer.valueOf(4), parsed.getMinimumIdle());
    }
}
