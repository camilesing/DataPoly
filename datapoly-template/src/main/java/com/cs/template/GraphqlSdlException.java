// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

/**
 * Raised when a GraphQL SDL document fails to parse, to satisfy the root-field {@code @sql}
 * contract, or to assemble into an executable schema.
 */
public class GraphqlSdlException extends RuntimeException {

    public GraphqlSdlException(String message) {
        super(message);
    }

    public GraphqlSdlException(String message, Throwable cause) {
        super(message, cause);
    }
}
