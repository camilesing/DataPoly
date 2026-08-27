// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.model;

@FunctionalInterface
public interface ThrowableRunnable {

    void run() throws Exception;
}
