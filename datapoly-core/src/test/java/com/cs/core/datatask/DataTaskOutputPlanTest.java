// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.enums.DataTypeFormatEnum;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

public class DataTaskOutputPlanTest {

    @Test
    public void identityKeepsAllColumnsAndAppliesAliasOnly() {
        Map<String, String> alias = new LinkedHashMap<>();
        alias.put("b", "乙");

        DataTaskOutputPlan plan = DataTaskOutputPlan.resolve(
                Arrays.asList("a", "b"), alias, Collections.emptyList(), false, Collections.emptyMap());

        Assert.assertEquals(Arrays.asList("a", "乙"), plan.getOutputColumns());
        Assert.assertArrayEquals(new Object[]{1, 2}, plan.project(new Object[]{1, 2}));
    }

    @Test
    public void explicitOrderSubsetsReordersAndIgnoresUnknownOrDuplicateEntries() {
        DataTaskOutputPlan plan = DataTaskOutputPlan.resolve(
                Arrays.asList("a", "b"), Collections.emptyMap(),
                Arrays.asList("b", "nope", "a", "b"), false, Collections.emptyMap());

        Assert.assertEquals(Arrays.asList("b", "a"), plan.getOutputColumns());
        // raw_b slot first: positional projection follows the reordered mapping
        Assert.assertArrayEquals(new Object[]{"y", "x"}, plan.project(new Object[]{"x", "y"}));
    }

    @Test
    public void missingAliasFallsBackToConvertedLabel() {
        DataTaskOutputPlan plan = DataTaskOutputPlan.resolve(
                Collections.singletonList("col_x"), null, null, false, null);

        Assert.assertEquals(Collections.singletonList("col_x"), plan.getOutputColumns());
        Assert.assertArrayEquals(new Object[]{null}, plan.project(new Object[0]));
    }

    @Test
    public void stringifyFormatsTimestampCellsByPattern() {
        Map<DataTypeFormatEnum, String> formats = new LinkedHashMap<>();
        formats.put(DataTypeFormatEnum.TIMESTAMP, "yyyy*MM*dd HH|mm|ss");
        Timestamp ts = new Timestamp(1260000000000L);

        DataTaskOutputPlan plan = DataTaskOutputPlan.resolve(
                Collections.singletonList("t"), Collections.emptyMap(), Collections.emptyList(), true, formats);

        Object[] out = plan.project(new Object[]{ts});
        Assert.assertEquals(new SimpleDateFormat("yyyy*MM*dd HH|mm|ss").format(ts), out[0]);
        // non-temporal values pass through untouched
        Assert.assertEquals("plain", plan.project(new Object[]{"plain"})[0]);
    }

    @Test
    public void stringifyScalesBigDecimalsWithHalfUp() {
        Map<DataTypeFormatEnum, String> formats = new LinkedHashMap<>();
        formats.put(DataTypeFormatEnum.BIG_DECIMAL, "2");

        DataTaskOutputPlan plan = DataTaskOutputPlan.resolve(
                Collections.singletonList("v"), Collections.emptyMap(), Collections.emptyList(), true, formats);

        Assert.assertEquals("1.24", plan.project(new Object[]{new BigDecimal("1.235")})[0]);
        Assert.assertEquals("2.00", plan.project(new Object[]{new BigDecimal(2)})[0]);
    }

    @Test
    public void stringifyHandlesJavaTimeTypesAndSkippedWhenToggleOff() {
        Map<DataTypeFormatEnum, String> formats = new LinkedHashMap<>();
        formats.put(DataTypeFormatEnum.LOCAL_DATE_TIME, "yyyy/MM/dd");
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 10, 30);

        DataTaskOutputPlan on = DataTaskOutputPlan.resolve(
                Collections.singletonList("d"), Collections.emptyMap(), Collections.emptyList(), true, formats);
        Assert.assertEquals("2026/08/27", on.project(new Object[]{now})[0]);

        DataTaskOutputPlan off = DataTaskOutputPlan.resolve(
                Collections.singletonList("d"), Collections.emptyMap(), Collections.emptyList(), false, formats);
        Assert.assertSame(now, off.project(new Object[]{now})[0]);
    }

    @Test
    public void selectProjectsParallelPerColumnListsInTheShapedOrder() {
        DataTaskOutputPlan plan = DataTaskOutputPlan.resolve(
                Arrays.asList("a", "b", "c"), Collections.emptyMap(),
                Arrays.asList("c", "a"), false, Collections.emptyMap());

        Assert.assertEquals(Arrays.asList("META_C", "META_A"),
                plan.select(Arrays.asList("META_A", "META_B", "META_C")));
        // missing entries fall in as nulls, never throwing
        Assert.assertEquals(Arrays.asList(null, "META_B"),
                plan.select(Collections.singletonList("META_B")));
        Assert.assertTrue(plan.select(null).isEmpty());
    }
}
