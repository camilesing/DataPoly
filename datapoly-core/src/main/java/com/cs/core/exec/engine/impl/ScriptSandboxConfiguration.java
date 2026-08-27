// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine.impl;

import cn.hutool.extra.spring.SpringUtil;
import groovy.lang.*;
import groovy.transform.ThreadInterrupt;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.*;
import org.springframework.core.env.Environment;

import java.util.*;

/**
 * Groovy script sandbox configuration: blacklist interception based on {@link SecureASTCustomizer} plus expression checkers,
 * forbidding scripts from reaching system commands, reflection, arbitrary file/network/classloading and other dangerous capabilities (S1).
 *
 * <p>Can be disabled entirely via {@code datapoly.executor.script.sandbox-enabled=false} (escape hatch).
 */
public final class ScriptSandboxConfiguration {

    private static final String KEY_SANDBOX_ENABLED = "datapoly.executor.script.sandbox-enabled";
    private static final String KEY_TIMEOUT_SECONDS = "datapoly.executor.script.timeout-seconds";
    private static final boolean DEFAULT_SANDBOX_ENABLED = true;
    private static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    private static volatile Boolean sandboxEnabled;
    private static volatile Long timeoutSeconds;

    private static volatile boolean runtimeCommandGuardInstalled = false;

    /**
     * Runtime command-execution backstop (S1): at compile time {@code def l=['sh']; l.execute()} cannot be distinguished from the legitimate
     * {@code http.execute()} (both receivers have compile-time type Object), so execute/exec is disabled directly on the concrete classes of
     * common command-execution receivers (Groovy dispatches by concrete class, not interface). Module objects (e.g. http) are not in the list
     * and are unaffected. Residual surface (exotic List implementations etc.) is documented in AGENTS.md.
     */
    private static final String RUNTIME_COMMAND_GUARD_SCRIPT =
            "['java.lang.String','groovy.lang.GString','java.util.ArrayList','java.util.LinkedList',\n"
                    + " 'java.util.Vector','java.util.Stack','java.util.Arrays$ArrayList'].each { className ->\n"
                    + "  def clazz = Class.forName(className)\n"
                    + "  def mc = new groovy.lang.ExpandoMetaClass(clazz, false, true)\n"
                    + "  // 按参数元数注册（Groovy 方法选择按元数匹配，需覆盖 DGM 的各档签名）\n"
                    + "  mc.registerInstanceMethod('execute') { ->\n"
                    + "    throw new SecurityException('Script command execution (execute) is not allowed')\n"
                    + "  }\n"
                    + "  mc.registerInstanceMethod('execute') { a ->\n"
                    + "    throw new SecurityException('Script command execution (execute) is not allowed')\n"
                    + "  }\n"
                    + "  mc.registerInstanceMethod('execute') { a, b ->\n"
                    + "    throw new SecurityException('Script command execution (execute) is not allowed')\n"
                    + "  }\n"
                    + "  mc.registerInstanceMethod('exec') { ->\n"
                    + "    throw new SecurityException('Script command execution (exec) is not allowed')\n"
                    + "  }\n"
                    + "  mc.initialize()\n"
                    + "  groovy.lang.GroovySystem.getMetaClassRegistry().setMetaClass(clazz, mc)\n"
                    + "}";

    /**
     * Types scripts must not reference (matched by fully qualified name, covering imports, FQ references, constructors and method/property receivers).
     */
    private static final Set<String> DISALLOWED_CLASS_TYPES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    // JVM dangerous entry points
                    "java.lang.System",
                    "java.lang.Runtime",
                    "java.lang.ProcessBuilder",
                    "java.lang.Class",
                    "java.lang.ClassLoader",
                    "java.lang.Compiler",
                    "java.lang.SecurityManager",
                    "java.lang.Thread",
                    "java.lang.ThreadGroup",
                    // Reflection
                    "java.lang.reflect.Method",
                    "java.lang.reflect.Field",
                    "java.lang.reflect.Constructor",
                    "java.lang.reflect.Array",
                    "java.lang.reflect.Proxy",
                    "java.lang.reflect.InvocationHandler",
                    "java.lang.invoke.MethodHandles",
                    // File and IO
                    "java.io.File",
                    "java.io.FileInputStream",
                    "java.io.FileOutputStream",
                    "java.io.FileReader",
                    "java.io.FileWriter",
                    "java.io.RandomAccessFile",
                    "java.nio.file.Files",
                    "java.nio.file.Paths",
                    "java.nio.file.FileSystems",
                    // Networking (outbound network access goes through the http module only)
                    "java.net.URL",
                    "java.net.URLConnection",
                    "java.net.HttpURLConnection",
                    "java.net.Socket",
                    "java.net.ServerSocket",
                    "java.net.DatagramSocket",
                    // Script engines / dynamic class loading (every entry point that can spawn unsandboxed nested scripts)
                    "javax.script.ScriptEngineManager",
                    "javax.script.ScriptEngine",
                    "groovy.lang.GroovyShell",
                    "groovy.lang.GroovyClassLoader",
                    "groovy.util.Eval",
                    "groovy.ui.Console",
                    "groovy.text.SimpleTemplateEngine",
                    "groovy.text.GStringTemplateEngine",
                    "groovy.text.StreamingTemplateEngine",
                    "groovy.text.XmlTemplateEngine",
                    "org.codehaus.groovy.runtime.InvokerHelper",
                    "sun.misc.Unsafe",
                    "groovy.grape.Grape")));

    /**
     * Packages whose wildcard import is forbidden.
     */
    private static final Set<String> DISALLOWED_STAR_IMPORTS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "java.lang.reflect",
                    "javax.script",
                    "javax.naming",
                    "javax.management",
                    "java.io",
                    "java.nio.file",
                    "java.net",
                    "sun.misc",
                    "groovy.ui",
                    "groovy.grape",
                    "groovy.text",
                    "org.codehaus.groovy.runtime")));

    /**
     * Reflection entry method names: forbidden regardless of receiver type.
     */
    private static final Set<String> DISALLOWED_REFLECTION_METHODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("forName", "loadClass")));

    /**
     * Command-execution vectors: execute()/exec() offered by Groovy DGM on String/collection receivers.
     */
    private static final Set<String> DISALLOWED_COMMAND_METHODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("execute", "exec")));

    private static final Set<String> DISALLOWED_COMMAND_RECEIVERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "java.lang.String",
                    "java.lang.CharSequence",
                    "groovy.lang.GString",
                    "java.util.List",
                    "java.util.Collection",
                    "java.util.ArrayList",
                    "java.util.LinkedList",
                    "java.util.Arrays$ArrayList",
                    "[Ljava.lang.String;",
                    "[Ljava.lang.Object;")));

    private ScriptSandboxConfiguration() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isSandboxEnabled() {
        if (null == sandboxEnabled) {
            synchronized (ScriptSandboxConfiguration.class) {
                if (null == sandboxEnabled) {
                    sandboxEnabled = Boolean.parseBoolean(
                            getProperty(KEY_SANDBOX_ENABLED, String.valueOf(DEFAULT_SANDBOX_ENABLED)));
                }
            }
        }
        return sandboxEnabled;
    }

    public static long getScriptTimeoutSeconds() {
        if (null == timeoutSeconds) {
            synchronized (ScriptSandboxConfiguration.class) {
                if (null == timeoutSeconds) {
                    try {
                        timeoutSeconds = Long.parseLong(
                                getProperty(KEY_TIMEOUT_SECONDS, String.valueOf(DEFAULT_TIMEOUT_SECONDS)));
                    } catch (NumberFormatException e) {
                        timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
                    }
                }
            }
        }
        return timeoutSeconds;
    }

    public static GroovyShell createGroovyShell(Binding binding) {
        if (!isSandboxEnabled()) {
            return new GroovyShell(binding);
        }
        installRuntimeCommandGuard();
        return new GroovyShell(binding, buildCompilerConfiguration());
    }

    private static void installRuntimeCommandGuard() {
        if (runtimeCommandGuardInstalled) {
            return;
        }
        synchronized (ScriptSandboxConfiguration.class) {
            if (runtimeCommandGuardInstalled) {
                return;
            }
            new GroovyShell().evaluate(RUNTIME_COMMAND_GUARD_SCRIPT);
            runtimeCommandGuardInstalled = true;
        }
    }

    private static CompilerConfiguration buildCompilerConfiguration() {
        SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();
        secureCustomizer.setImportsBlacklist(new java.util.ArrayList<>(DISALLOWED_CLASS_TYPES));
        secureCustomizer.setStaticImportsBlacklist(new java.util.ArrayList<>(DISALLOWED_CLASS_TYPES));
        secureCustomizer.setStarImportsBlacklist(new java.util.ArrayList<>(DISALLOWED_STAR_IMPORTS));
        secureCustomizer.setReceiversBlackList(new java.util.ArrayList<>(DISALLOWED_CLASS_TYPES));
        secureCustomizer.setIndirectImportCheckEnabled(true);
        secureCustomizer.addExpressionCheckers(ScriptSandboxConfiguration::isExpressionAuthorized);

        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(secureCustomizer);
        // Make loops/method bodies respond to thread interruption so Future.cancel(true) works on infinite-loop scripts
        configuration.addCompilationCustomizers(
                new ASTTransformationCustomizer(ThreadInterrupt.class));
        // Disable @Grab dynamic dependency fetching
        configuration.setDisabledGlobalASTTransformations(
                new HashSet<>(Arrays.asList("groovy.grape.GrabAnnotationTransformation")));
        return configuration;
    }

    private static boolean isExpressionAuthorized(Expression expression) {
        if (expression instanceof MethodPointerExpression) {
            return false;
        }
        if (expression instanceof ClassExpression) {
            return !DISALLOWED_CLASS_TYPES.contains(expression.getType().getName());
        }
        if (expression instanceof ConstructorCallExpression) {
            ConstructorCallExpression call = (ConstructorCallExpression) expression;
            return !DISALLOWED_CLASS_TYPES.contains(call.getType().getName());
        }
        if (expression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expression;
            String methodName = call.getMethodAsString();
            if (null == methodName) {
                return true;
            }
            if (DISALLOWED_REFLECTION_METHODS.contains(methodName)) {
                return false;
            }
            if (DISALLOWED_COMMAND_METHODS.contains(methodName)
                    && DISALLOWED_COMMAND_RECEIVERS.contains(
                    call.getObjectExpression().getType().getName())) {
                return false;
            }
        }
        return true;
    }

    private static String getProperty(String key, String defaultValue) {
        try {
            Environment environment = SpringUtil.getBean(Environment.class);
            String value = environment.getProperty(key);
            return StringUtils.isBlank(value) ? defaultValue : value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
