// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface FlowControlManger {

    boolean checkFlowControl(String resourceName, HttpServletResponse response) throws IOException;
}
