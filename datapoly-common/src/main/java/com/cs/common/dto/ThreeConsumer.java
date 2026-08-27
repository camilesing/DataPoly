// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

@FunctionalInterface
public interface ThreeConsumer<X, Y, Z> {

    void accept(X arg1, Y arg2, Z arg3);
}
