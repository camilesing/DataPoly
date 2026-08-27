// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.serdes.datetime;

import cn.hutool.core.date.DatePattern;
import com.cs.core.util.JacksonUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class TimeValueSerializer extends StdSerializer<Time> {

    private static final String DEFAULT_PATTERN = DatePattern.NORM_TIME_PATTERN;

    private String pattern;

    public TimeValueSerializer(String pattern) {
        super(Time.class);
        this.pattern = StringUtils.defaultIfBlank(pattern, DEFAULT_PATTERN);
    }

    @Override
    public void serialize(Time value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (value != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            // Read timezone from config; fall back to Asia/Shanghai if not configured
            sdf.setTimeZone(TimeZone.getTimeZone(JacksonUtils.getTimezone()));
            jsonGenerator.writeString(sdf.format(value));
        }
    }
}
