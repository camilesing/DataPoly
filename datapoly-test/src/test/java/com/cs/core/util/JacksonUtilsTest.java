// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.OutParam;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.core.serdes.datetime.DateValueSerializer;
import com.cs.core.serdes.datetime.InstantValueSerializer;
import com.cs.core.serdes.datetime.LocalDateValueSerializer;
import com.cs.core.serdes.datetime.LocalDateTimeValueSerializer;
import com.cs.core.serdes.datetime.TimeValueSerializer;
import com.cs.core.serdes.datetime.TimestampValueSerializer;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Field-type discovery over nested query results (an empty collection sibling must be
 * skipped, never abort parsing of the items after it), JSON serialization honoring the
 * configured {@code JSON_TIMEZONE} and per-type format patterns, and the fill-null-as-string
 * pass over discovered field types.
 */
public class JacksonUtilsTest {

    /** Mutable property source behind the stub Environment. */
    private static final Map<String, String> SPRING_PROPERTIES = new HashMap<>();

    @Before
    public void installEnvironment() throws Exception {
        SPRING_PROPERTIES.clear();
        Environment environment = (Environment) Proxy.newProxyInstance(
                JacksonUtilsTest.class.getClassLoader(),
                new Class[]{Environment.class}, (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName()) && args != null && args.length >= 1) {
                        String value = SPRING_PROPERTIES.get(String.valueOf(args[0]));
                        if (value != null) {
                            return value;
                        }
                        return args.length > 1 ? args[args.length - 1] : null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(JacksonUtilsTest.class.getClassLoader(),
                new Class[]{ConfigurableListableBeanFactory.class}, (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class) {
                        return environment;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubBeanFactory";
                    }
                    return null;
                });
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
        clearTimezoneCache();
    }

    @After
    public void uninstallEnvironment() throws Exception {
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
        clearTimezoneCache();
    }

    /** JacksonUtils caches the resolved timezone statically; every test recomputes it. */
    private static void clearTimezoneCache() throws Exception {
        Field cache = JacksonUtils.class.getDeclaredField("timeZoneMap");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).clear();
    }

    private static List<String> fieldNames(List<OutParam> params) {
        List<String> names = new ArrayList<>();
        for (OutParam param : params) {
            names.add(param.getName());
        }
        return names;
    }

    private static OutParam find(List<OutParam> params, String name) {
        for (OutParam param : params) {
            if (name.equals(param.getName())) {
                return param;
            }
        }
        throw new AssertionError("field not discovered: " + name);
    }

    @Test
    public void emptyCollectionSiblingDoesNotDropFollowingItems() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("alpha", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("beta", 2);
        List<Object> rows = Arrays.asList(first, new ArrayList<>(), second);

        List<OutParam> results = JacksonUtils.parseFieldTypes(rows);

        assertEquals(Arrays.asList("alpha", "beta"), fieldNames(results));
    }

    @Test
    public void emptyNestedCollectionSiblingKeepsThirdLevelItems() {
        Map<String, Object> deep = new LinkedHashMap<>();
        deep.put("gamma", 3);
        List<Object> rows = Arrays.asList(
                Arrays.asList(new ArrayList<>()),   // empty second-level collection
                Arrays.asList(deep));

        List<OutParam> results = JacksonUtils.parseFieldTypes(rows);

        assertEquals(Collections.singletonList("gamma"), fieldNames(results));
    }

    @Test
    public void plainMapTopLevelStillParses() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("alpha", 1);
        row.put("beta", "x");

        List<OutParam> results = JacksonUtils.parseFieldTypes(row);

        assertEquals(Arrays.asList("alpha", "beta"), fieldNames(results));
    }

    @Test
    public void nonCollectionNonMapObjectsYieldNoFields() {
        // a bare scalar and an empty collection both produce an empty discovery list
        assertTrue(JacksonUtils.parseFieldTypes(42).isEmpty());
        assertTrue(JacksonUtils.parseFieldTypes(new ArrayList<>()).isEmpty());
    }

    @Test
    public void nonMapNonCollectionItemsAreSkipped() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("alpha", 1);
        List<Object> rows = Arrays.asList("plain-item", row);

        List<OutParam> results = JacksonUtils.parseFieldTypes(rows);

        assertEquals(Collections.singletonList("alpha"), fieldNames(results));
    }

    @Test
    public void thirdLevelCollectionsAreWalked() {
        Map<String, Object> deep = new LinkedHashMap<>();
        deep.put("gamma", 3);
        // collection -> collection -> collection-of-map: the third level must be walked,
        // non-map siblings on the way are skipped
        List<Object> rows = Arrays.asList(
                Arrays.asList(Collections.singletonList(deep), "skip-me"),
                Collections.singletonList(Collections.singletonList("scalar")));

        List<OutParam> results = JacksonUtils.parseFieldTypes(rows);

        assertEquals(Collections.singletonList("gamma"), fieldNames(results));
    }

    @Test
    public void emptyThirdLevelCollectionIsSkippedWithoutAborting() {
        Map<String, Object> deep = new LinkedHashMap<>();
        deep.put("delta", 4);
        List<Object> rows = Arrays.asList(
                Collections.singletonList(Collections.emptyList()),  // empty third-level
                Collections.singletonList(deep));

        List<OutParam> results = JacksonUtils.parseFieldTypes(rows);

        assertEquals(Collections.singletonList("delta"), fieldNames(results));
    }

    @Test
    public void duplicateTopLevelFieldNamesAreReportedOnce() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("alpha", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("alpha", 2);
        second.put("beta", 3);

        List<OutParam> results = JacksonUtils.parseFieldTypes(Arrays.asList(first, second));

        assertEquals(Arrays.asList("alpha", "beta"), fieldNames(results));
    }

    @Test
    public void arrayValuedFieldsExposeElementTypesAndNestedObjects() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("k", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("empty", new ArrayList<>());
        row.put("arrOfArr", Collections.singletonList(new ArrayList<>()));
        row.put("arrScalar", Collections.singletonList(1));
        row.put("arrMap", Collections.singletonList(inner));

        List<OutParam> results = JacksonUtils.parseFieldTypes(row);

        assertTrue(find(results, "empty").getIsArray());
        assertNull(find(results, "empty").getType());
        assertEquals(ParamTypeEnum.LONG, find(results, "arrScalar").getType());
        assertEquals(ParamTypeEnum.OBJECT, find(results, "arrMap").getType());
        assertEquals(Collections.singletonList("k"), fieldNames(find(results, "arrMap").getChildren()));
        // array of empty arrays: no element type can be discovered
        assertNull(find(results, "arrOfArr").getType());
    }

    @Test
    public void fieldTypesCoverTheWholeValueMatrix() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("b", true);
        row.put("by", (byte) 1);
        row.put("i", 1);
        row.put("l", 1L);
        row.put("bi", BigInteger.ONE);
        row.put("d", 1.5D);
        row.put("f", 1.5F);
        row.put("t", new Time(0L));
        row.put("ts", new Timestamp(0L));
        row.put("ldt", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        row.put("in", Instant.EPOCH);
        row.put("ud", new Date(0L));
        row.put("sqlD", new java.sql.Date(0L));
        row.put("ld", LocalDate.of(2026, 1, 2));
        row.put("s", "x");
        row.put("m", new HashMap<>());
        row.put("c", new ArrayList<>());

        List<OutParam> results = JacksonUtils.parseFiledTypesAndFillNullAsString(row);

        assertEquals(ParamTypeEnum.BOOLEAN, find(results, "b").getType());
        assertEquals(ParamTypeEnum.BOOLEAN, find(results, "by").getType());
        assertEquals(ParamTypeEnum.LONG, find(results, "i").getType());
        assertEquals(ParamTypeEnum.LONG, find(results, "l").getType());
        assertEquals(ParamTypeEnum.LONG, find(results, "bi").getType());
        assertEquals(ParamTypeEnum.DOUBLE, find(results, "d").getType());
        assertEquals(ParamTypeEnum.DOUBLE, find(results, "f").getType());
        assertEquals(ParamTypeEnum.TIME, find(results, "t").getType());
        assertEquals(ParamTypeEnum.TIME, find(results, "ts").getType());
        assertEquals(ParamTypeEnum.TIME, find(results, "ldt").getType());
        assertEquals(ParamTypeEnum.TIME, find(results, "in").getType());
        assertEquals(ParamTypeEnum.DATE, find(results, "ud").getType());
        assertEquals(ParamTypeEnum.DATE, find(results, "sqlD").getType());
        assertEquals(ParamTypeEnum.DATE, find(results, "ld").getType());
        assertEquals(ParamTypeEnum.STRING, find(results, "s").getType());
        assertEquals(ParamTypeEnum.OBJECT, find(results, "m").getType());
        // empty array: no element type discovered, filled as STRING by the pass
        assertEquals(ParamTypeEnum.STRING, find(results, "c").getType());
    }

    @Test
    public void fillNullAsStringAlsoReachesChildren() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("xs", new ArrayList<>());
        inner.put("n", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rows", Collections.singletonList(inner));

        List<OutParam> results = JacksonUtils.parseFiledTypesAndFillNullAsString(row);

        OutParam rows = find(results, "rows");
        assertEquals(ParamTypeEnum.OBJECT, rows.getType());
        OutParam xs = find(rows.getChildren(), "xs");
        assertEquals(ParamTypeEnum.STRING, xs.getType());
        assertEquals(ParamTypeEnum.LONG, find(rows.getChildren(), "n").getType());
    }

    @Test
    public void fillNullAsStringLeavesTypedChildrenAlone() {
        // children exist but none needs filling (the not-empty / non-null-type branches)
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("n", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rows", Collections.singletonList(inner));
        row.put("plain", "v");

        List<OutParam> results = JacksonUtils.parseFiledTypesAndFillNullAsString(row);

        assertEquals(ParamTypeEnum.LONG, find(find(results, "rows").getChildren(), "n").getType());
        // a scalar field has no children list at all: pass must not choke
        assertEquals(ParamTypeEnum.STRING, find(results, "plain").getType());
    }

    @Test
    public void toJsonStrUsesDefaultTimeZoneWhenUnset() {
        // no JSON_TIMEZONE property: Asia/Shanghai default -> epoch renders 08:00:00
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ts", new Timestamp(0L));
        String json = JacksonUtils.toJsonStr(payload, null);
        assertTrue(json, json.contains("1970-01-01 08:00:00"));
    }

    @Test
    public void toJsonStrHonorsConfiguredTimeZone() {
        SPRING_PROPERTIES.put("JSON_TIMEZONE", "UTC");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ts", new Timestamp(0L));
        String json = JacksonUtils.toJsonStr(payload, null);
        assertTrue(json, json.contains("1970-01-01 00:00:00"));
    }

    @Test
    public void toJsonStrAppliesConfiguredFormatPatterns() {
        Map<DataTypeFormatEnum, String> formats = new EnumMap<>(DataTypeFormatEnum.class);
        formats.put(DataTypeFormatEnum.TIMESTAMP, "yyyy/MM/dd HH:mm");
        formats.put(DataTypeFormatEnum.LOCAL_DATE, "dd.MM.yyyy");
        formats.put(DataTypeFormatEnum.BIG_DECIMAL, "3");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ts", new Timestamp(0L));
        payload.put("d", LocalDate.of(2026, 1, 2));
        payload.put("amount", new BigDecimal("3.141592653589"));

        String json = JacksonUtils.toJsonStr(payload, formats);

        assertTrue(json, json.contains("1970/01/01 08:00"));
        assertTrue(json, json.contains("02.01.2026"));
        assertTrue(json, json.contains("3.141"));
    }

    @Test
    public void toJsonStrCoversEveryTemporalSerializer() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("time", new Time(0L));
        payload.put("sqlDate", new java.sql.Date(0L));
        payload.put("localDt", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        payload.put("instant", Instant.EPOCH);

        String json = JacksonUtils.toJsonStr(payload, null);

        // all rendered in Asia/Shanghai
        assertTrue(json, json.contains("08:00:00"));            // Time
        assertTrue(json, json.contains("1970-01-01"));          // sqlDate / instant date part
        assertTrue(json, json.contains("2026-01-02 03:04:05")); // localDateTime (zone-free)
    }

    @Test
    public void toJsonStrScalesNumbersByConfiguredBigDecimalScale() {
        Map<DataTypeFormatEnum, String> formats = new EnumMap<>(DataTypeFormatEnum.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", new BigDecimal("3.141592653589"));

        // no explicit scale: default 6 (floor)
        assertEquals(6, new java.math.BigDecimal(
                JacksonUtils.toJsonStr(payload, formats).replaceAll("[^0-9.]", "")).scale());
    }

    /** A bean whose getter explodes: Jackson's JsonProcessingException must be wrapped. */
    public static class FailingGetterBean {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    public void toJsonStrWrapsSerializationFailuresAsRuntimeException() {
        try {
            JacksonUtils.toJsonStr(new FailingGetterBean(), null);
            fail("serialization failure must surface");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException);
        }
    }

    /**
     * The null-value guard inside every temporal/number serializer: Jackson itself routes
     * nulls through the null serializer, so the guard is exercised directly.
     */
    @Test
    public void temporalSerializersWriteNothingForNullValues() throws Exception {
        assertNullSerialization(new TimestampValueSerializer(null));
        assertNullSerialization(new TimeValueSerializer(null));
        assertNullSerialization(new DateValueSerializer(null));
        assertNullSerialization(new LocalDateValueSerializer(null));
        assertNullSerialization(new LocalDateTimeValueSerializer(null));
        assertNullSerialization(new InstantValueSerializer(null));
        assertNullSerialization(new com.cs.core.serdes.number.NumberValueSerializer(null));
    }

    private void assertNullSerialization(com.fasterxml.jackson.databind.ser.std.StdSerializer<?> serializer)
            throws Exception {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = new JsonFactory().createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeFieldName("v");
            serializer.serialize(null, generator, null);
            generator.writeEndObject();
            generator.flush();
        }
        // Jackson emits the field-name colon together with the value, so a skipped value
        // leaves just the bare name inside the object
        assertEquals("null value must not be written", "{\"v\"}", writer.toString());
    }
}
