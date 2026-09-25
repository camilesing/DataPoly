// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * Connection-test failures must reach the caller as a business error carrying the driver message,
 * instead of the generic internal error that hides the reason (permission denied, unknown host, ...).
 */
public class DataSourceServiceConnectFailureTest {

    @Test
    public void surfacesDeepestDriverMessage() {
        Throwable odpsFailure = new RuntimeException("java.sql.SQLException: execute sql [ SELECT 1 FROM dual; ] + failed.",
                new Exception("com.aliyun.odps.OdpsException: ODPS-0420095: Access Denied",
                        new IllegalStateException("ODPS-0420095: Access Denied - Authorization Failed [4009,4019], "
                                + "You have NO privilege 'odps:CreateInstance' on {acs:odps:*:projects/df_cs_205568}.\n"
                                + "\t--->Tips: Principal:RAM$bestfulfill:data_dev_maxcomputer;")));

        String message = DataSourceService.causeMessage(odpsFailure);

        assertTrue(message, message.contains("NO privilege 'odps:CreateInstance'"));
        assertFalse(message, message.contains("\n"));
        assertFalse(message, message.contains("\t"));
    }

    @Test
    public void fallsBackToOuterMessageThenClassName() {
        assertEquals("connect refused", DataSourceService.causeMessage(
                new RuntimeException("connect refused", new IllegalStateException())));

        Throwable messageLess = new RuntimeException(new IllegalStateException());
        assertTrue(DataSourceService.causeMessage(messageLess).contains("IllegalStateException"));
    }

    @Test
    public void capsOverlongDriverDump() {
        StringBuilder dump = new StringBuilder();
        while (dump.length() < 2000) {
            dump.append("ODPS-0420095: Access Denied; ");
        }

        assertTrue(DataSourceService.causeMessage(new RuntimeException(dump.toString())).length() <= 500);
    }

    @Test
    public void wrapsAsBusinessErrorKeepingTheCause() {
        RuntimeException driverFailure = new RuntimeException("boom");

        CommonException exception = DataSourceService.connectFailure(driverFailure);

        assertEquals(ResponseErrorCode.ERROR_CANNOT_CONNECT_REMOTE, exception.getCode());
        assertEquals(200, exception.getCode().getHttpStatus());
        assertSame(driverFailure, exception.getCause());
    }
}