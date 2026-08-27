// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import com.cs.common.consts.Constants;

public enum AliveTimeEnum {
    PERIOD("短期", Constants.CLIENT_TOKEN_DURATION_SECONDS),
    LONGEVITY("长期", Constants.CLIENT_TOKEN_LONGEVITY);

    private String name;
    private long value;

    AliveTimeEnum(String name, long value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public long getValue() {
        return value;
    }
}
