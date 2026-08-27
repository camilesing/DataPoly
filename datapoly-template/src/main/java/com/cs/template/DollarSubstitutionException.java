// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

/**
 * Thrown when ${} literal substitution is forbidden (S3); the caller (core layer) converts it into a generic business error.
 */
public class DollarSubstitutionException extends RuntimeException {

    public DollarSubstitutionException(String message) {
        super(message);
    }
}
