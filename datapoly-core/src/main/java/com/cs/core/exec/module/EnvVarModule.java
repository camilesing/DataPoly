// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import org.springframework.core.env.Environment;

/**
 * Script-facing {@code env} module. Binding the raw Spring {@link Environment} would expose
 * every configured property (Redis password, AES key, login credentials...) to user scripts,
 * so the script instance is created with an allowlist prefix: only properties explicitly
 * published under {@code datapoly.script.env.*} are visible, resolution never falls back to
 * arbitrary keys. Fail closed — unset names return null (or the given default).
 */
@Module(EnvVarModule.VAR_NAME)
public class EnvVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "env";

    /** Allowlist prefix for values scripts may read; publish a value by setting this property. */
    public static final String SCRIPT_ENV_PREFIX = "datapoly.script.env.";

    private final Environment environment;
    private final String keyPrefix;

    public EnvVarModule(Environment environment) {
        this(environment, null);
    }

    public EnvVarModule(Environment environment, String keyPrefix) {
        this.environment = environment;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    private String resolveKey(String key) {
        return (null == keyPrefix) ? key : keyPrefix + key;
    }

    @Comment("comment.env.get")
    public String get(@Comment("comment.param.key") String key) {
        return environment.getProperty(resolveKey(key));
    }

    @Comment("comment.env.get")
    public String get(@Comment("comment.param.key") String key,
                      @Comment("comment.param.defaultValue") String defaultValue) {
        return environment.getProperty(resolveKey(key), defaultValue);
    }

}
