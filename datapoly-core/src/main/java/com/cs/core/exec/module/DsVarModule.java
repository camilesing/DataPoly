// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.service.VarModuleInterface;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.exec.ExecutorMetadataCache;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import com.cs.core.util.DataSourceUtils;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.DataSourceEntity;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.util.Map;

@Module(DsVarModule.VAR_NAME)
public class DsVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "ds";

    private DataSourceDao dataSourceDao = SpringUtil.getBean(DataSourceDao.class);
    private DriverLoadService driverLoadService = SpringUtil.getBean(DriverLoadService.class);
    private ExecutorMetadataCache metadataCache = SpringUtil.getBean(ExecutorMetadataCache.class);

    private Map<String, Object> params;
    private NamingStrategyEnum strategy;
    private boolean printSqlLog;

    public DsVarModule(Map<String, Object> params, NamingStrategyEnum strategy, boolean printSqlLog) {
        this.params = params;
        this.strategy = strategy;
        this.printSqlLog = printSqlLog;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    @Comment("comment.ds.getDB")
    public DbVarModule getDB(@Comment("comment.param.id") Long id) {
        DataSourceEntity dsEntity = metadataCache.getDataSource(id, () -> dataSourceDao.getById(id));
        if (null == dsEntity) {
            throw new RuntimeException("Not found id=" + id + " data source!");
        }
        File driverPath = driverLoadService.getVersionDriverFile(dsEntity.getType(), dsEntity.getVersion());
        HikariDataSource dataSource = DataSourceUtils.getHikariDataSource(dsEntity, driverPath.getAbsolutePath());
        return new DbVarModule(dataSource, dsEntity.getType(), params, strategy, printSqlLog);
    }
}

