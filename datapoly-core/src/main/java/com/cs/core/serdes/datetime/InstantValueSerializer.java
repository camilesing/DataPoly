// Use of this source code is governed by a BSD-style license
package com.cs.core.serdes.datetime;

import cn.hutool.core.date.DatePattern;
import com.cs.core.util.JacksonUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Serializes {@link Instant} the way the other temporal serializers do: in the configured
 * {@code JSON_TIMEZONE}, with the configured pattern. Some JDBC drivers return an
 * {@link Instant} for timestamp columns instead of a {@link java.sql.Timestamp}; without this
 * serializer the whole API response (debug, online call and MCP share
 * {@link JacksonUtils#toJsonStr}) fails with "Java 8 date/time type not supported by default".
 */
public class InstantValueSerializer extends StdSerializer<Instant> {

    private static final String DEFAULT_PATTERN = DatePattern.NORM_DATETIME_PATTERN;

    private final String pattern;

    public InstantValueSerializer(String pattern) {
        super(Instant.class);
        this.pattern = StringUtils.defaultIfBlank(pattern, DEFAULT_PATTERN);
    }

    @Override
    public void serialize(Instant value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (value != null) {
            jsonGenerator.writeString(DateTimeFormatter.ofPattern(pattern)
                    .withZone(zone())
                    .format(value));
        }
    }

    private static ZoneId zone() {
        // TimeZone tolerates the unset/unknown zone names JacksonUtils falls back to; ZoneId.of does not
        return TimeZone.getTimeZone(JacksonUtils.getTimezone()).toZoneId();
    }

}