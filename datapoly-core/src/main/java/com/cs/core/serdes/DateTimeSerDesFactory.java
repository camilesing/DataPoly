// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.serdes;

import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.core.serdes.datetime.*;
import com.cs.core.serdes.number.NumberValueSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.ImmutableMap;

import java.util.*;
import java.util.function.Function;

public final class DateTimeSerDesFactory {

    private static Map<DataTypeFormatEnum, Function<String, StdSerializer>> DATE_TIME_SER_MAP = new HashMap<>();

    static {
        DATE_TIME_SER_MAP.put(DataTypeFormatEnum.DATE, DateValueSerializer::new);
        DATE_TIME_SER_MAP.put(DataTypeFormatEnum.LOCAL_DATE, LocalDateValueSerializer::new);

        DATE_TIME_SER_MAP.put(DataTypeFormatEnum.TIME, TimeValueSerializer::new);

        DATE_TIME_SER_MAP.put(DataTypeFormatEnum.TIMESTAMP, TimestampValueSerializer::new);
        DATE_TIME_SER_MAP.put(DataTypeFormatEnum.LOCAL_DATE_TIME, LocalDateTimeValueSerializer::new);

        DATE_TIME_SER_MAP.put(DataTypeFormatEnum.BIG_DECIMAL, NumberValueSerializer::new);
    }

    public static Map<DataTypeFormatEnum, Function<String, StdSerializer>> getAllSerDesMap() {
        return ImmutableMap.copyOf(DATE_TIME_SER_MAP);
    }
}
