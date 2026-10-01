// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import com.cs.core.exec.logger.DebugExecuteLogger;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Service;

@Slf4j
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
        // a trailing Throwable is not part of the formatted message: keep it out of the
        // caller-visible debug log and surface it in the server log instead
        FormattingTuple tuple = MessageFormatter.arrayFormat(message, arguments);
        DebugExecuteLogger.add(tuple.getMessage());
        if (null != tuple.getThrowable()) {
            log.error("Script log printed with throwable: {}", tuple.getMessage(), tuple.getThrowable());
        }
    }
}
