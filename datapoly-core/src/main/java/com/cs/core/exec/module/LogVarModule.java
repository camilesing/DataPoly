// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import com.cs.core.exec.logger.DebugExecuteLogger;
import lombok.NoArgsConstructor;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Service;

@Service
@NoArgsConstructor
@Module(LogVarModule.VAR_NAME)
public class LogVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "log";

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    @Comment("comment.log.print")
    public void print(@Comment("comment.param.message") String message) {
        DebugExecuteLogger.add(message);
    }

    @Comment("comment.log.print")
    public void print(@Comment("comment.param.message") String message, @Comment("comment.param.arguments") Object... arguments) {
        // https://blog.csdn.net/weixin_44792849/article/details/131854226
        DebugExecuteLogger.add(MessageFormatter.arrayFormat(message, arguments).getMessage());
    }
}
