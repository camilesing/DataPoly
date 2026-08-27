// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.serdes.datetime;

import cn.hutool.core.date.DatePattern;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateValueSerializer extends StdSerializer<LocalDate> {

    private static final String DEFAULT_PATTERN = DatePattern.NORM_DATE_PATTERN;

    private String pattern;

    public LocalDateValueSerializer(String pattern) {
        super(LocalDate.class);
        this.pattern = StringUtils.defaultIfBlank(pattern, DEFAULT_PATTERN);
    }

    @Override
    public void serialize(LocalDate value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (value != null) {
            // Use DateTimeFormatter instead of SimpleDateFormat for LocalDate
            // Read timezone from config; fall back to Asia/Shanghai if not configured
            jsonGenerator.writeString(value.format(DateTimeFormatter.ofPattern(pattern)));
        }
    }
}
