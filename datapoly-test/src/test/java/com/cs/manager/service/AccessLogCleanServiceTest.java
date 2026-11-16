// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.mapper.AccessRecordMapper;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * The housekeeping job must run the delete on both entry points (startup + nightly cron)
 * with the configured retention, and a failing delete is logged, never propagated —
 * a broken sweep must not kill the application or the schedule.
 */
public class AccessLogCleanServiceTest {

    private RecordingMapper recorder;
    private AccessLogCleanService service;

    /** Proxy mapper that records deleteHistoryBeforeDays calls and can be told to fail. */
    private static class RecordingMapper {
        final List<Integer> daysArgument = new ArrayList<>();
        Throwable failure;

        AccessRecordMapper create() {
            return (AccessRecordMapper) Proxy.newProxyInstance(AccessRecordMapper.class.getClassLoader(),
                    new Class<?>[]{AccessRecordMapper.class},
                    (p, m, a) -> {
                        if ("deleteHistoryBeforeDays".equals(m.getName())) {
                            if (failure != null) {
                                throw failure;
                            }
                            daysArgument.add((Integer) a[0]);
                        }
                        Class<?> rt = m.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        return null;
                    });
        }
    }

    @Before
    public void setUp() {
        recorder = new RecordingMapper();
        service = new AccessLogCleanService();
        DataTaskTestSupport.setField(service, "cleanJobLogDays", 30);
        DataTaskTestSupport.setField(service, "accessRecordMapper", recorder.create());
    }

    @Test
    public void restartSweepDeletesWithConfiguredRetention() {
        service.cleanOnceAfterRestart();
        assertEquals(Collections.singletonList(Integer.valueOf(30)), recorder.daysArgument);
    }

    @Test
    public void scheduledSweepAlsoRunsTheDelete() {
        service.cleanSchedule();
        assertEquals(1, recorder.daysArgument.size());
        assertEquals(Integer.valueOf(30), recorder.daysArgument.get(0));
    }

    @Test
    public void retentionComesFromConfigurationNotHardcoded() {
        DataTaskTestSupport.setField(service, "cleanJobLogDays", 90);
        service.cleanSchedule();
        assertEquals(Integer.valueOf(90), recorder.daysArgument.get(0));
    }

    @Test
    public void aFailingDeleteIsSwallowed() {
        recorder.failure = new RuntimeException("table missing");
        service.cleanOnceAfterRestart(); // must not throw
        assertTrue(recorder.daysArgument.isEmpty());

        recorder.failure = null;
        service.cleanSchedule(); // recovered once the failure is gone
        assertEquals(Integer.valueOf(30), recorder.daysArgument.get(0));
    }

    @Test
    public void legacyThrowableAlsoSwallowed() {
        recorder.failure = new Error("database vanished");
        service.cleanSchedule(); // catch (Throwable) covers Errors too
        assertTrue(recorder.daysArgument.isEmpty());
    }
}
