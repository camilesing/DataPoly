// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.service.DisplayRecord;

public class ScripDebugRecord implements DisplayRecord {

    private String text;

    public ScripDebugRecord(String text) {
        this.text = text;
    }

    @Override
    public String getDisplayText() {
        return text;
    }
}
