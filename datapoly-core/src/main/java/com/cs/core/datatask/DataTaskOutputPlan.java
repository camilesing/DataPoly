// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.enums.DataTypeFormatEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * Resolves how raw query columns become delivered columns: subset/reorder by the
 * declared order, rename through aliases (keys refer to labels AFTER the naming
 * strategy conversion), optionally stringify date/time/decimal cells following the
 * {@link DataTypeFormatEnum} pattern profile. Pure data structure, safe to reuse.
 */
public class DataTaskOutputPlan {

    @Getter
    private final List<String> outputColumns;

    /** output slot i reads from this index of the raw column array */
    private final int[] keepIndex;

    private final boolean stringifyCells;

    private final List<Map.Entry<DataTypeFormatEnum, String>> formatRules;

    private DataTaskOutputPlan(List<String> outputColumns, int[] keepIndex,
                               boolean stringifyCells, List<Map.Entry<DataTypeFormatEnum, String>> formatRules) {
        this.outputColumns = outputColumns;
        this.keepIndex = keepIndex;
        this.stringifyCells = stringifyCells;
        this.formatRules = formatRules;
    }

    public static DataTaskOutputPlan resolve(List<String> sourceColumns,
                                             Map<String, String> alias,
                                             List<String> order,
                                             boolean stringifyCells,
                                             Map<DataTypeFormatEnum, String> formats) {
        Objects.requireNonNull(sourceColumns, "sourceColumns");
        Map<String, String> aliasMap = null == alias ? Collections.emptyMap() : alias;
        List<String> selected = new ArrayList<>();
        int[] keep;
        if (null == order || order.isEmpty()) {
            keep = new int[sourceColumns.size()];
            for (int i = 0; i < sourceColumns.size(); i++) {
                selected.add(aliasOrDefault(sourceColumns.get(i), aliasMap));
                keep[i] = i;
            }
        } else {
            Set<String> seen = new HashSet<>();
            List<Integer> keeps = new ArrayList<>();
            for (String wanted : order) {
                int idx = null == wanted ? -1 : sourceColumns.indexOf(wanted);
                // entries not matching any column are ignored; unlisted columns are dropped;
                // a repeated entry is delivered only once
                if (idx >= 0 && seen.add(wanted)) {
                    selected.add(aliasOrDefault(wanted, aliasMap));
                    keeps.add(idx);
                }
            }
            keep = new int[keeps.size()];
            for (int i = 0; i < keeps.size(); i++) {
                keep[i] = keeps.get(i);
            }
        }

        List<Map.Entry<DataTypeFormatEnum, String>> rules = new ArrayList<>();
        if (stringifyCells && null != formats) {
            for (Map.Entry<DataTypeFormatEnum, String> entry : formats.entrySet()) {
                if (DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT != entry.getKey()) {
                    rules.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(),
                            StringUtils.isNotBlank(entry.getValue())
                                    ? entry.getValue().trim()
                                    : entry.getKey().getDefaultPattern()));
                }
            }
        }
        return new DataTaskOutputPlan(Collections.unmodifiableList(selected), keep, stringifyCells, rules);
    }

    private static String aliasOrDefault(String column, Map<String, String> aliasMap) {
        String alias = aliasMap.get(column);
        return StringUtils.isBlank(alias) ? column : alias.trim();
    }

    /** Project one raw row to the shaped output row (positional, parallel to getOutputColumns). */
    public Object[] project(Object[] cells) {
        Object[] out = new Object[outputColumns.size()];
        for (int i = 0; i < out.length; i++) {
            int src = keepIndex[i];
            Object value = (src < cells.length) ? cells[src] : null;
            if (stringifyCells) {
                value = stringify(value);
            }
            out[i] = value;
        }
        return out;
    }

    /** Project a per-source-column parallel list (e.g. type metadata) to the shaped output order. */
    public <T> List<T> select(List<T> source) {
        if (null == source) {
            return Collections.emptyList();
        }
        List<T> out = new ArrayList<>(keepIndex.length);
        for (int i = 0; i < keepIndex.length; i++) {
            int src = keepIndex[i];
            out.add(src < source.size() ? source.get(src) : null);
        }
        return out;
    }

    private Object stringify(Object value) {
        if (null == value) {
            return null;
        }
        for (Map.Entry<DataTypeFormatEnum, String> rule : formatRules) {
            DataTypeFormatEnum type = rule.getKey();
            Class<?> target = typeClasses().get(type);
            if (null != target && target.isInstance(value)) {
                if (DataTypeFormatEnum.BIG_DECIMAL == type && value instanceof BigDecimal) {
                    int scale = parseScale(rule.getValue());
                    return ((BigDecimal) value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
                }
                return formatTemporal(value, rule.getValue());
            }
        }
        return value;
    }

    private static final Map<DataTypeFormatEnum, Class<?>> TYPE_CLASSES = new EnumMap<>(DataTypeFormatEnum.class);

    private static synchronized Map<DataTypeFormatEnum, Class<?>> typeClasses() {
        if (TYPE_CLASSES.isEmpty()) {
            for (DataTypeFormatEnum type : DataTypeFormatEnum.values()) {
                if (DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT == type) {
                    continue;
                }
                try {
                    TYPE_CLASSES.put(type, Class.forName(type.getClassName(), false, DataTaskOutputPlan.class.getClassLoader()));
                } catch (Throwable ignore) {
                    // jdbc type class not present in this JVM — the rule simply never matches
                }
            }
        }
        return TYPE_CLASSES;
    }

    private static int parseScale(String pattern) {
        try {
            return Math.max(0, Integer.parseInt(pattern));
        } catch (NumberFormatException e) {
            return DataTypeFormatEnum.BIG_DECIMAL.getNumberScale();
        }
    }

    private static String formatTemporal(Object value, String pattern) {
        try {
            if (value instanceof java.util.Date) {
                // Timestamp/java.sql.Date/Time all subclass java.util.Date
                return new SimpleDateFormat(pattern).format((java.util.Date) value);
            }
            if (value instanceof TemporalAccessor) {
                return DateTimeFormatter.ofPattern(pattern).format((TemporalAccessor) value);
            }
        } catch (Exception ignore) {
            // fall through to the raw value on any pattern mismatch
        }
        return String.valueOf(value);
    }
}
