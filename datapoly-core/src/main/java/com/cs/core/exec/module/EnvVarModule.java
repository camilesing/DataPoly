// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Module(EnvVarModule.VAR_NAME)
public class EnvVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "env";

    private final Environment environment;

    public EnvVarModule(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    @Comment("comment.env.get")
    public String get(@Comment("comment.param.key") String key) {
        return environment.getProperty(key);
    }

    @Comment("comment.env.get")
    public String get(@Comment("comment.param.key") String key,
                      @Comment("comment.param.defaultValue") String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

}
