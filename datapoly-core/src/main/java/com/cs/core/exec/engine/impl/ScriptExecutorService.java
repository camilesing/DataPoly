// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.common.service.VarModuleInterface;
import com.cs.core.dto.ScriptEditorCompletion;
import com.cs.core.exec.annotation.Module;
import com.cs.core.exec.engine.AbstractExecutorEngine;
import com.cs.core.exec.module.*;
import com.cs.persistence.entity.ApiContextEntity;
import com.zaxxer.hikari.HikariDataSource;
import groovy.lang.*;
import org.codehaus.groovy.control.CompilationFailedException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScriptExecutorService extends AbstractExecutorEngine {

    public static List<ScriptEditorCompletion> syntax = new ArrayList<>();
    public static List<Class> modules = Arrays.asList(
            EnvVarModule.class,
            HttpModule.class,
            LogVarModule.class,
            DbVarModule.class,
            TxVarModule.class,
            DsVarModule.class,
            ReqVarModule.class,
            CacheVarModule.class);

    static {
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("foreach")
                        .caption("foreach")
                        .value("for(item in collection){\n\t\n}")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("for")
                        .caption("fori")
                        .value("for(i=0;i< ;i++){\n\t\n}")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("for")
                        .caption("for")
                        .value("for( ){\n\t\n}")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("if")
                        .caption("if")
                        .value("if( ){\n\n}")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("if")
                        .caption("ifelse")
                        .value("if( ){\n\t\n}else{\n\t\n}")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("import")
                        .caption("import")
                        .value("import ")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("continue")
                        .caption("continue")
                        .value("continue;")
                        .build());
        syntax.add(
                ScriptEditorCompletion.builder()
                        .meta("break")
                        .caption("break")
                        .value("break;")
                        .build());
    }

    public static String getModuleVarName(Class clazz) {
        if (clazz.isAnnotationPresent(Module.class)) {
            Module annotation = (Module) clazz.getAnnotation(Module.class);
            return annotation.value();
        }
        return "unknown";
    }

    ////////////////////////////////////////////////////////////////////////////////////////

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private static final ThreadFactory SCRIPT_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "script-executor-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };

    /**
     * Dedicated bounded thread pool for script execution: provides execution timeout and interruption via Future.get(timeout) (S1).
     * Rejects when the queue is full to prevent script pile-up from exhausting request threads.
     */
    private static final ExecutorService SCRIPT_EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            2 * Runtime.getRuntime().availableProcessors(),
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256),
            SCRIPT_THREAD_FACTORY,
            new ThreadPoolExecutor.AbortPolicy());

    static {
        ((ThreadPoolExecutor) SCRIPT_EXECUTOR).allowCoreThreadTimeOut(true);
    }

    public ScriptExecutorService(HikariDataSource dataSource, ProductTypeEnum productType) {
        super(dataSource, productType);
    }

    private String getModuleVarName(VarModuleInterface varModule) {
        return varModule.getVarModuleName();
    }

    private List<VarModuleInterface> getAllVarModules(Map<String, Object> params, NamingStrategyEnum strategy,
                                                      boolean dollarSubstitutionAllowed) {
        List<VarModuleInterface> moduleList = new ArrayList<>();
        Map<String, VarModuleInterface> moduleBeans = SpringUtil.getBeansOfType(VarModuleInterface.class);
        for (Class<?> moduleClass : modules) {
            for (VarModuleInterface bean : moduleBeans.values()) {
                if (moduleClass.isAssignableFrom(bean.getClass())) {
                    moduleList.add(bean);
                    break;
                }
            }
        }
        moduleList.add(new ReqVarModule(params));
        moduleList.add(new DsVarModule(params, strategy, printSqlLog));
        moduleList.add(new DbVarModule(dataSource, productType, params, strategy, printSqlLog, dollarSubstitutionAllowed));
        return moduleList;
    }

    @Override
    public List<Object> execute(List<ApiContextEntity> scripts, Map<String, Object> params, NamingStrategyEnum strategy,
                                boolean dollarSubstitutionAllowed) {
        List<VarModuleInterface> varModuleList = getAllVarModules(params, strategy, dollarSubstitutionAllowed);
        long timeoutSeconds = ScriptSandboxConfiguration.getScriptTimeoutSeconds();

        List<Object> results = new ArrayList<>();
        for (ApiContextEntity entity : scripts) {
            Binding binding = new Binding();
            params.forEach((k, v) -> binding.setProperty(k, v));
            varModuleList.forEach(m -> binding.setProperty(getModuleVarName(m), m));

            GroovyShell groovyShell = ScriptSandboxConfiguration.createGroovyShell(binding);
            Future<Object> future = null;
            try {
                future = SCRIPT_EXECUTOR.submit(() -> (Object) groovyShell.evaluate(entity.getSqlText()));
                results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
            } catch (RejectedExecutionException e) {
                throw new CommonException(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS,
                        "Script execute rejected: too many concurrent scripts");
            } catch (TimeoutException e) {
                if (null != future) {
                    future.cancel(true);
                }
                throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR,
                        String.format("Script execute timeout after [%d] seconds", timeoutSeconds));
            } catch (ExecutionException e) {
                Throwable cause = (null != e.getCause()) ? e.getCause() : e;
                if (cause instanceof CompilationFailedException
                        || cause instanceof RuntimeException
                        || cause instanceof Error) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (null != future) {
                    future.cancel(true);
                }
                throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "Script execute interrupted");
            }
        }
        return results;
    }

}
