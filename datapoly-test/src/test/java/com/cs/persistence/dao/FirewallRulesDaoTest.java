// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.cs.common.enums.OnOffEnum;
import com.cs.common.enums.WhiteBlackEnum;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.entity.FirewallRulesEntity;
import com.cs.persistence.mapper.FirewallRulesMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * The firewall state is a single fixed row (id=1): reads fetch it by that id and writes
 * target it — including the toggle shortcuts that only patch the status column.
 */
public class FirewallRulesDaoTest {

    private static class Recorder {
        final Map<String, List<Object[]>> calls = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        <M> M create(Class<M> type) {
            return (M) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, m, a) -> {
                        calls.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(a);
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

        Object[] argsOf(String method) {
            return calls.get(method).get(0);
        }
    }

    private Recorder recorder;
    private FirewallRulesDao dao;

    @BeforeClass
    public static void initEntityMetadata() {
        com.cs.persistence.PersistenceTestSupport.initTableInfo(FirewallRulesEntity.class);
    }

    @Before
    public void setUp() {
        recorder = new Recorder();
        dao = new FirewallRulesDao();
        DataTaskTestSupport.setField(dao, "firewallRulesMapper", recorder.create(FirewallRulesMapper.class));
    }

    @Test
    public void getFirewallRulesReadsTheSingleFixedRow() {
        dao.getFirewallRules();
        assertEquals(Long.valueOf(1L), recorder.argsOf("selectById")[0]);
    }

    @Test
    public void togglesPatchOnlyTheStatusColumn() {
        dao.turnOn();
        assertEquals(Long.valueOf(1L), recorder.argsOf("updateStatus")[0]);
        assertEquals(OnOffEnum.ON, recorder.argsOf("updateStatus")[1]);

        dao.turnOff();
        Object[] offArgs = recorder.calls.get("updateStatus").get(1);
        assertEquals(Long.valueOf(1L), offArgs[0]);
        assertEquals(OnOffEnum.OFF, offArgs[1]);
    }

    @Test
    public void updateRebuildsTheWholeFixedRow() {
        dao.update(OnOffEnum.ON, WhiteBlackEnum.WHITE, "10.0.0.0/8,192.168.0.0/16");

        FirewallRulesEntity written = (FirewallRulesEntity) recorder.argsOf("updateById")[0];
        assertEquals(Long.valueOf(1L), written.getId());
        assertEquals(OnOffEnum.ON, written.getStatus());
        assertEquals(WhiteBlackEnum.WHITE, written.getMode());
        assertEquals("10.0.0.0/8,192.168.0.0/16", written.getAddresses());
    }
}
