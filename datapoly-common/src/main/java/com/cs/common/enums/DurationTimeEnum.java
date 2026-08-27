// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import lombok.Getter;

@Getter
public enum DurationTimeEnum {

    FOR_EVER(-1),
    ONLY_ONCE(0),
    TIME_VALUE(1),
    ;

    private long value;

    DurationTimeEnum(long value) {
        this.value = value;
    }

}
