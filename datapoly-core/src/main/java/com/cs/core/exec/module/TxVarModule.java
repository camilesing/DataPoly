// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;

@Module(TxVarModule.VAR_NAME)
public class TxVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "tx";

    private final TransactionDefinition transactionDefinition;
    private final PlatformTransactionManager transactionManager;
    private final TransactionStatus transactionStatus;

    public TxVarModule(DataSource dataSource) {
        this.transactionDefinition = getDefaultTransactionDefinition();
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        this.transactionStatus = this.transactionManager.getTransaction(this.transactionDefinition);
    }

    private TransactionDefinition getDefaultTransactionDefinition() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return definition;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    @Comment("comment.tx.commit")
    public void commit() {
        this.transactionManager.commit(this.transactionStatus);
    }

    @Comment("comment.tx.rollback")
    public void rollback() {
        this.transactionManager.rollback(this.transactionStatus);
    }
}
