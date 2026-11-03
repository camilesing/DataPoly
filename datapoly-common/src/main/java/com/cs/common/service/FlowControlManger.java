// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface FlowControlManger {

    /**
     * @param resourceName flow resource the request counts against
     * @param response      used to write the 429 body when blocked
     * @return a lease to close after the guarded request completes, or {@code null} when the
     *         request is blocked (the response has already been written)
     */
    FlowControlLease checkFlowControl(String resourceName, HttpServletResponse response) throws IOException;
}
