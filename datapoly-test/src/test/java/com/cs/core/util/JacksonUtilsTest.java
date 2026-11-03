// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.dto.OutParam;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Field-type discovery over nested query results: an empty collection sibling must be
 * skipped, never abort parsing of the items after it.
 */
public class JacksonUtilsTest {

    private static List<String> fieldNames(List<OutParam> params) {
        List<String> names = new ArrayList<>();
        for (OutParam param : params) {
            names.add(param.getName());
        }
        return names;
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
}
