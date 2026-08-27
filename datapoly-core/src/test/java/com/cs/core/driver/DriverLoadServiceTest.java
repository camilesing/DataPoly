// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.driver;

import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.*;
import org.junit.*;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Driver directory lookup contract (H3): missing type or version throws a controlled business exception instead of an NPE; a hit returns the directory file.
 */
public class DriverLoadServiceTest {

    private DriverLoadService service;
    private File mysqlDriverFile;

    @Before
    public void setUp() {
        service = new DriverLoadService();
        mysqlDriverFile = new File("/tmp/drivers/mysql/8.0.33");
        Map<String, File> versions = new HashMap<>();
        versions.put("8.0.33", mysqlDriverFile);
        service.drivers.put(ProductTypeEnum.MYSQL, versions);
    }

    @Test
    public void returnsFileWhenTypeAndVersionExist() {
        assertSame(mysqlDriverFile,
                service.getVersionDriverFile(ProductTypeEnum.MYSQL, "8.0.33"));
    }

    @Test
    public void missingTypeThrowsInsteadOfNpe() {
        try {
            service.getVersionDriverFile(ProductTypeEnum.ORACLE, "11g");
            fail("should throw for missing type");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void missingVersionThrowsInsteadOfNull() {
        try {
            service.getVersionDriverFile(ProductTypeEnum.MYSQL, "5.1.47");
            fail("should throw for missing version");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

}
