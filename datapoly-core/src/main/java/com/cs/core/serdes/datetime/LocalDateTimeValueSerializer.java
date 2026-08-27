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
import java.time.*;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeValueSerializer extends StdSerializer<LocalDateTime> {

    private static final String DEFAULT_PATTERN = DatePattern.NORM_DATETIME_PATTERN;

    private String pattern;

    public LocalDateTimeValueSerializer(String pattern) {
        super(LocalDateTime.class);
        this.pattern = StringUtils.defaultIfBlank(pattern, DEFAULT_PATTERN);
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (value != null) {
            // Use DateTimeFormatter instead of SimpleDateFormat for LocalDateTime
            // Read timezone from config; fall back to Asia/Shanghai if not configured
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withZone(ZoneId.of(JacksonUtils.getTimezone()));
            jsonGenerator.writeString(value.format(DateTimeFormatter.ofPattern(pattern)));
        }
    }
}
