// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;

import java.util.Map;

@Module(ReqVarModule.VAR_NAME)
public class ReqVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "req";

    private Map<String, Object> params;

    public ReqVarModule(Map<String, Object> params) {
        this.params = params;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    @Comment("comment.req.setParam")
    public void setParam(@Comment("comment.param.paramName") String name, @Comment("comment.param.paramValue") Object value) {
        this.params.put(name, value);
    }
}
