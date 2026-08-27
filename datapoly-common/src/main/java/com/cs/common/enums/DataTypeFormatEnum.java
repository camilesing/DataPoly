// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import cn.hutool.core.date.DatePattern;
import com.cs.common.util.I18nUtils;

public enum DataTypeFormatEnum {
    DATE(java.sql.Date.class.getName(), DatePattern.NORM_DATE_PATTERN),
    LOCAL_DATE(java.time.LocalDate.class.getName(), DatePattern.NORM_DATE_PATTERN),

    TIME(java.sql.Time.class.getName(), DatePattern.NORM_TIME_PATTERN),

    LOCAL_DATE_TIME(java.time.LocalDateTime.class.getName(), DatePattern.NORM_DATETIME_PATTERN),
    TIMESTAMP(java.sql.Timestamp.class.getName(), DatePattern.NORM_DATETIME_PATTERN),

    BIG_DECIMAL(java.math.BigDecimal.class.getName(), 6),

    // System response format config: true = return the full ResultEntity, false = return only the data property
    USE_SYSTEM_RESPONSE_FORMAT(null, "format.use.system.response", "true"),
    ;

    private String className;
    private String remarkKey;
    private String defaultPattern;
    private int numberScale;

    DataTypeFormatEnum(String className, String defaultPattern) {
        this.className = className;
        this.defaultPattern = defaultPattern;
    }

    DataTypeFormatEnum(String className, String remarkKey, String defaultPattern) {
        this.className = className;
        this.remarkKey = remarkKey;
        this.defaultPattern = defaultPattern;
    }

    DataTypeFormatEnum(String className, int numberScale) {
        this.className = className;
        this.numberScale = numberScale;
    }

    public String getClassName() {
        return className;
    }

    public String getRemark() {
        if (remarkKey != null) {
            return I18nUtils.getMessage(remarkKey);
        }
        return className;
    }

    public String getDefaultPattern() {
        return defaultPattern;
    }

    public int getNumberScale() {
        return numberScale;
    }

    public String getDefault() {
        if (numberScale > 0) {
            return String.valueOf(numberScale);
        }
        return defaultPattern;
    }
}
