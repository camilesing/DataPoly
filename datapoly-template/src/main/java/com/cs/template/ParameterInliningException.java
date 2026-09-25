// Use of this source code is governed by a BSD-style license
package com.cs.template;

/**
 * Thrown when the bind placeholders of a rendered statement cannot be turned into SQL
 * literals — a value that is not a scalar, or a placeholder/value count that does not
 * agree. The caller (core layer) records the message on the failed job, so it is written
 * as authoring guidance.
 */
public class ParameterInliningException extends RuntimeException {

    public ParameterInliningException(String message) {
        super(message);
    }
}