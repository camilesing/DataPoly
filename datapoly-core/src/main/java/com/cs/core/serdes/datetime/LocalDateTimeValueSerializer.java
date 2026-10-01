// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.serdes.datetime;

import cn.hutool.core.date.DatePattern;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalDateTimeValueSerializer extends StdSerializer<LocalDateTime> {

    private static final String DEFAULT_PATTERN = DatePattern.NORM_DATETIME_PATTERN;

    /**
     * Formatters are immutable; assembling one per serialize() call is wasted work on hot
     * response paths, so they are cached per pattern (patterns come from API config).
     */
    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

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
            jsonGenerator.writeString(value.format(
                    FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern)));
        }
    }
}
