// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction handle for scripts ({@code db.beginTx()}). The constructor opens a pooled
 * connection; a script that returns or throws without commit/rollback would hold it forever
 * (and the thread-bound {@code TransactionSynchronizationManager} state would leak into the
 * next script on the same pool thread). Every instance therefore registers itself in a
 * thread-local registry and {@link ScriptExecutorService} rolls back the leftovers on the
 * script thread right after evaluation — rollback must run on the owning thread.
 */
@Slf4j
@Module(TxVarModule.VAR_NAME)
public class TxVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "tx";

    private static final ThreadLocal<List<TxVarModule>> OPEN_ON_THREAD = new ThreadLocal<>();

    private final TransactionDefinition transactionDefinition;
    private final PlatformTransactionManager transactionManager;
    private final TransactionStatus transactionStatus;

    public TxVarModule(DataSource dataSource) {
        this.transactionDefinition = getDefaultTransactionDefinition();
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        this.transactionStatus = this.transactionManager.getTransaction(this.transactionDefinition);
        registry().add(this);
    }

    private static List<TxVarModule> registry() {
        List<TxVarModule> open = OPEN_ON_THREAD.get();
        if (null == open) {
            open = new ArrayList<>();
            OPEN_ON_THREAD.set(open);
        }
        return open;
    }

    /**
     * Rolls back every transaction still open on the current thread and clears the registry.
     * Called by the script executor on the script thread after each evaluation.
     */
    public static void rollbackLeftoversOnCurrentThread() {
        List<TxVarModule> open = OPEN_ON_THREAD.get();
        if (null == open) {
            return;
        }
        OPEN_ON_THREAD.remove();
        for (TxVarModule tx : open) {
            if (!tx.transactionStatus.isCompleted()) {
                try {
                    tx.transactionManager.rollback(tx.transactionStatus);
                    log.warn("Script left an open transaction, rolled back on cleanup");
                } catch (TransactionException e) {
                    log.error("Failed to roll back abandoned script transaction: {}", e.getMessage(), e);
                }
            }
        }
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
