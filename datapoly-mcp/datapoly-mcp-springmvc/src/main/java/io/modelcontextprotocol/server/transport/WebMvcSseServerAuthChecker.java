// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package io.modelcontextprotocol.server.transport;

public interface WebMvcSseServerAuthChecker {

    String getTokenParamName();

    boolean checkTokenValid(String token);
}
