// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Result handed back by a sink once the delivery finished successfully.
 * artifactUri is whatever frontends need to consume the payload (a download URL,
 * an object-store key, ...); info carries additional structured details that are
 * stored verbatim (as JSON) on the job record.
 */
@Data
@Builder
public class SinkOutcome implements Serializable {

    private String artifactUri;

    private Map<String, Object> info;

    public static SinkOutcome empty() {
        return SinkOutcome.builder().build();
    }
}
