// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.core.dto.DataTaskBaseResponse;
import com.cs.core.dto.DataTaskJobView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.Assert.assertTrue;

/**
 * Job timestamps must reach the browser as Beijing time. The MVC mapper configures no
 * time-zone of its own, so a pattern-only {@code @JsonFormat} silently falls back to
 * Jackson's UTC default: 02:48 UTC would be shown for a job started at 10:48 Beijing.
 */
public class DataTaskTimeZoneJsonTest {

    /** 2026-09-20 02:48:34 UTC == 2026-09-20 10:48:34 Beijing */
    private static final Timestamp BEIJING_TEN_48 = Timestamp.from(Instant.parse("2026-09-20T02:48:34Z"));

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void jobViewRendersBeijingTime() throws Exception {
        String json = mapper.writeValueAsString(DataTaskJobView.builder()
                .id(27L)
                .startTime(BEIJING_TEN_48)
                .finishTime(BEIJING_TEN_48)
                .createTime(BEIJING_TEN_48)
                .build());
        assertTrue(json, json.contains("\"startTime\":\"2026-09-20 10:48:34\""));
        assertTrue(json, json.contains("\"finishTime\":\"2026-09-20 10:48:34\""));
        assertTrue(json, json.contains("\"createTime\":\"2026-09-20 10:48:34\""));
    }

    @Test
    public void baseResponseRendersBeijingTime() throws Exception {
        String json = mapper.writeValueAsString(DataTaskBaseResponse.builder()
                .id(4L)
                .createTime(BEIJING_TEN_48)
                .updateTime(BEIJING_TEN_48)
                .build());
        assertTrue(json, json.contains("\"createTime\":\"2026-09-20 10:48:34\""));
        assertTrue(json, json.contains("\"updateTime\":\"2026-09-20 10:48:34\""));
    }
}