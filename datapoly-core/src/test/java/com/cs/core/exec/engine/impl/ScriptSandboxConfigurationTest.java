// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine.impl;

import groovy.lang.*;
import org.junit.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

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

}
