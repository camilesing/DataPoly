// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import cn.hutool.json.JSONUtil;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.Function;

@Getter
public enum ParamTypeEnum {
    LONG("整型", "number", 0L, Long.class, (String str) -> StringUtils.isNotBlank(str) ? Long.valueOf(str) : null),
    DOUBLE("浮点型", "number", 0D, Double.class, (String str) -> StringUtils.isNotBlank(str) ? Double.valueOf(str) : null),
    STRING("字符串", "string", "", String.class, (String str) -> str),
    DATE("日期", "string", "", String.class, (String str) -> str),
    TIME("时间", "string", "", String.class, (String str) -> str),
    //fix: show boolean type directly in swagger
    BOOLEAN("布尔", "boolean", "true", Boolean.class, (String str) -> StringUtils.isNotBlank(str) ? Boolean.parseBoolean(str) : null),
    //fix: empty-string default when object param is absent
    OBJECT("对象", "object", "{}", Map.class, (String str) -> StringUtils.isNotBlank(str) ? JSONUtil.toBean(str, Map.class) : null);

    private String name;
    private String jsType;
    private Object example;
    private Class clazz;
    private Function<String, Object> converter;

    ParamTypeEnum(String name, String jsType, Object example, Class clazz, Function<String, Object> converter) {
        this.name = name;
        this.jsType = jsType;
        this.example = example;
        this.clazz = clazz;
        this.converter = converter;
    }

    public boolean isObject() {
        return OBJECT == this;
    }
}
