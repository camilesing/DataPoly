// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.model;

import com.cs.common.dto.ResultEntity;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.core.exec.*;
import com.cs.core.util.JacksonUtils;
import com.cs.persistence.entity.ApiAssignmentEntity;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;

public class HttpApiServlet extends HttpServlet {

    private ApiExecuteService apiExecuteService;
    private boolean printSqlLog;

    public HttpApiServlet(ApiExecuteService apiExecuteService, boolean printSqlLog) {
        this.apiExecuteService = apiExecuteService;
        this.printSqlLog = printSqlLog;
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiAssignmentEntity apiConfigEntity = ApiAssignmentCache.get();
        ResultEntity result = apiExecuteService.execute(apiConfigEntity, request, printSqlLog);

        // Read USE_SYSTEM_RESPONSE_FORMAT from the responseFormat config
        String useSystemResponseFormat = apiConfigEntity.getResponseFormat().get(DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT);
        boolean useSystemFormat = !"false".equalsIgnoreCase(useSystemResponseFormat);

        String json;
        if (useSystemFormat) {
            // Return the full ResultEntity
            json = JacksonUtils.toJsonStr(result, apiConfigEntity.getResponseFormat());
        } else {
            // Return only the data part
            json = JacksonUtils.toJsonStr(result.getData(), apiConfigEntity.getResponseFormat());
        }

        response.getWriter().append(json);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }
}

