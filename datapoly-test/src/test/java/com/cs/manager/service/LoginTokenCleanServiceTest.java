// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.dao.LoginTokenDao;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The session sweeper runs on both entry points with a current-epoch cutoff and never
 * propagates a failure — it is housekeeping, not a request path.
 */
public class LoginTokenCleanServiceTest {

    private static class RecordingDao extends LoginTokenDao {
        final List<Long> cutoffs = new ArrayList<>();
        Throwable failure;

        @Override
        public int deleteExpired(long currentTimestamp) {
            if (failure != null) {
                throwUnchecked(failure);
            }
            cutoffs.add(currentTimestamp);
            return 3;
        }

        /** Sneaky-throw so the dao override can raise any Throwable (incl. Error). */
        @SuppressWarnings("unchecked")
        private static <T extends Throwable> void throwUnchecked(Throwable throwable) throws T {
            throw (T) throwable;
        }
    }

    private RecordingDao dao;
    private LoginTokenCleanService service;

    @Before
    public void setUp() {
        dao = new RecordingDao();
        service = new LoginTokenCleanService();
        DataTaskTestSupport.setField(service, "loginTokenDao", dao);
    }

    @Test
    public void restartSweepUsesCurrentEpochSeconds() {
        long before = System.currentTimeMillis() / 1000L;
        service.cleanOnceAfterRestart();
        long after = System.currentTimeMillis() / 1000L;

        assertEquals(1, dao.cutoffs.size());
        long cutoff = dao.cutoffs.get(0);
        assertTrue("cutoff should be epoch seconds taken around now",
                cutoff >= before && cutoff <= after);
    }

    @Test
    public void scheduledSweepAlsoRunsTheDelete() {
        service.cleanSchedule();
        assertEquals(1, dao.cutoffs.size());
    }

    @Test
    public void aFailingSweepIsSwallowedAndRetriesNextRun() {
        dao.failure = new RuntimeException("lock timeout");
        service.cleanOnceAfterRestart(); // must not throw
        service.cleanSchedule();          // must not throw either
        assertTrue(dao.cutoffs.isEmpty());

        dao.failure = null;
        service.cleanSchedule();
        assertEquals(1, dao.cutoffs.size());
    }

    @Test
    public void legacyThrowableAlsoSwallowed() {
        dao.failure = new Error("datasource down");
        service.cleanSchedule(); // catch (Throwable) covers Errors too
        assertTrue(dao.cutoffs.isEmpty());
    }
}
