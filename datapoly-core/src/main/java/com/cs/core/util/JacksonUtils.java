// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.OutParam;
import com.cs.common.enums.*;
import com.cs.common.util.UuidUtils;
import com.cs.core.serdes.DateTimeSerDesFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.env.Environment;

import java.math.BigInteger;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class JacksonUtils {

    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    private static final String JSON_TIMEZONE = "JSON_TIMEZONE";
    private static Map<String, String> timeZoneMap = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.disable(MapperFeature.IGNORE_DUPLICATE_MODULE_REGISTRATIONS);
        objectMapper.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
    }

    public static String getTimezone() {
        return timeZoneMap.computeIfAbsent(JSON_TIMEZONE,
                key -> {
                    try {
                        Environment env = SpringUtil.getBean(Environment.class);
                        return env.getProperty(JSON_TIMEZONE, DEFAULT_TIME_ZONE);
                    } catch (Exception e) {
                        // Fall back to the default timezone without a Spring container (plain unit tests)
                        return DEFAULT_TIME_ZONE;
                    }
                });
    }

    public static String toJsonStr(Object obj, Map<DataTypeFormatEnum, String> formatMap) {
        ObjectMapper mapper = objectMapper.copy();
        String currentTimeZone = getTimezone();
        if (!DEFAULT_TIME_ZONE.equals(currentTimeZone)) {
            mapper.setTimeZone(TimeZone.getTimeZone(currentTimeZone));
        }
        mapper.registerModule(createSerializeModule(formatMap));
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Module createSerializeModule(Map<DataTypeFormatEnum, String> formatMap) {
        Map<DataTypeFormatEnum, String> finalFormatMap = (null == formatMap) ? Collections.emptyMap() : formatMap;
        SimpleModule module = new SimpleModule();
        DateTimeSerDesFactory.getAllSerDesMap()
                .forEach(
                        (clazz, creator) -> {
                            StdSerializer serializer = creator.apply(finalFormatMap.get(clazz));
                            module.addSerializer(serializer.handledType(), serializer);
                        }
                );
        return module;
    }

    public static List<OutParam> parseFiledTypesAndFillNullAsString(Object obj) {
        List<OutParam> types = parseFieldTypes(obj);
        for (OutParam param : types) {
            if (null == param.getType()) {
                param.setType(ParamTypeEnum.STRING);
            }
            if (CollectionUtils.isNotEmpty(param.getChildren())) {
                for (OutParam subParam : param.getChildren()) {
                    if (null == subParam.getType()) {
                        subParam.setType(ParamTypeEnum.STRING);
                    }
                }
            }
        }
        return types;
    }

    public static List<OutParam> parseFieldTypes(Object obj) {
        List<OutParam> results = new LinkedList<>();
        if (null == obj) {
            return results;
        }

        if (obj instanceof Map) {
            parseFieldTypes(null, (Map) obj, results);
        } else if (obj instanceof Collection) {
            Collection collection = (Collection) obj;
            if (CollectionUtils.isEmpty(collection)) {
                return results;
            }
            for (Object item : collection) {
                if (item instanceof Map) {
                    parseFieldTypes(null, (Map) item, results);
                } else if (item instanceof Collection) {
                    Collection subCollection = (Collection) item;
                    if (CollectionUtils.isEmpty(subCollection)) {
                        return results;
                    }
                    for (Object subItem : subCollection) {
                        if (subItem instanceof Map) {
                            parseFieldTypes(null, (Map) subItem, results);
                        } else if (subItem instanceof Collection) {
                            Collection thSubCollection = (Collection) subItem;
                            if (CollectionUtils.isEmpty(thSubCollection)) {
                                return results;
                            }
                            for (Object thSubItem : thSubCollection) {
                                if (thSubItem instanceof Map) {
                                    parseFieldTypes(null, (Map) thSubItem, results);
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    private static void parseFieldTypes(OutParam parent, Map<String, Object> map, List<OutParam> results) {
        for (String name : map.keySet()) {
            Object value = map.get(name);
            boolean isArray = (value instanceof Collection);
            ParamTypeEnum typeEnum = parseValueType(value);
            String id = UuidUtils.generateUuid();
            OutParam outParam = new OutParam(id, name, typeEnum, isArray, null, Lists.newArrayList());
            if (isArray) {
                outParam.setType(parseValueType(((Collection) value).stream().findFirst().orElse(null)));
            }
            if (null == parent) {
                if (results.stream().noneMatch(one -> name.equals(one.getName()))) {
                    results.add(outParam);
                }
            } else {
                if (parent.getChildren().stream().noneMatch(one -> name.equals(one.getName()))) {
                    parent.getChildren().add(outParam);
                }
            }

            if (isArray) {
                Collection collection = (Collection) value;
                if (collection.size() > 0) {
                    Object item = collection.stream().findFirst().get();
                    ParamTypeEnum subTypeEnum = parseValueType(item);
                    if (null != subTypeEnum && subTypeEnum.isObject()) {
                        parseFieldTypes(outParam, (Map) item, results);
                    }
                }
            } else if (value instanceof Map) {
                parseFieldTypes(outParam, (Map) value, results);
            }
        }
    }

    private static ParamTypeEnum parseValueType(Object value) {
        if (value instanceof Boolean || value instanceof Byte) {
            return ParamTypeEnum.BOOLEAN;
        } else if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            return ParamTypeEnum.LONG;
        } else if (value instanceof Number) {
            return ParamTypeEnum.DOUBLE;
        } else if (value instanceof Time || value instanceof Timestamp || value instanceof LocalDateTime) {
            return ParamTypeEnum.TIME;
        } else if (value instanceof Date || value instanceof LocalDate) {
            return ParamTypeEnum.DATE;
        } else if (value instanceof String) {
            return ParamTypeEnum.STRING;
        } else if (value instanceof Map) {
            return ParamTypeEnum.OBJECT;
        } else {
            // Return null for List
            return null;
        }
    }
}
