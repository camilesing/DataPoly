// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.junit.*;

import java.math.BigDecimal;
import java.util.*;

public class SqlLiteralInlinerTest {

    private static List<Object> values(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static List<String> names(String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }

    @Test
    public void literalsAreTypedAndEscaped() {
        Assert.assertEquals("NULL", SqlLiteralInliner.literal(null, "p"));
        Assert.assertEquals("'abc'", SqlLiteralInliner.literal("abc", "p"));
        Assert.assertEquals("'O\\'Brien'", SqlLiteralInliner.literal("O'Brien", "p"));
        Assert.assertEquals("'a\\\\b'", SqlLiteralInliner.literal("a\\b", "p"));
        Assert.assertEquals("'导出'", SqlLiteralInliner.literal("导出", "p"));
        Assert.assertEquals("''", SqlLiteralInliner.literal("", "p"));
        Assert.assertEquals("42", SqlLiteralInliner.literal(42L, "p"));
        Assert.assertEquals("TRUE", SqlLiteralInliner.literal(Boolean.TRUE, "p"));
        Assert.assertEquals("FALSE", SqlLiteralInliner.literal(Boolean.FALSE, "p"));
        Assert.assertEquals("1.50", SqlLiteralInliner.literal(new BigDecimal("1.50"), "p"));
        Assert.assertEquals("10000000000", SqlLiteralInliner.literal(1.0E10D, "p"));
    }

    @Test
    public void valuesWithoutALiteralFormAreRefusedByName() {
        try {
            SqlLiteralInliner.literal(Double.NaN, "ratio");
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("ratio"));
        }
        try {
            SqlLiteralInliner.literal(Arrays.asList(1, 2), "ids");
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("ids"));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("foreach"));
        }
        try {
            SqlLiteralInliner.literal(new HashMap<String, Object>(), "filter");
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("filter"));
        }
    }

    @Test
    public void placeholdersAreReplacedInOrder() {
        String sql = SqlLiteralInliner.inline("select * from t where a = ? and b = ? and a > ?",
                values(1L, "x'y", null), names("a", "b", "c"));
        Assert.assertEquals("select * from t where a = 1 and b = 'x\\'y' and a > NULL", sql);
    }

    @Test
    public void questionMarksInsideQuotedTextAreNotPlaceholders() {
        Assert.assertEquals("select '?', 'v' from t",
                SqlLiteralInliner.inline("select '?', ? from t", values("v"), names("v")));
        Assert.assertEquals("select 'it''s ?', 'v' from t",
                SqlLiteralInliner.inline("select 'it''s ?', ? from t", values("v"), names("v")));
        Assert.assertEquals("select 'a\\' ? b', 'v' from t",
                SqlLiteralInliner.inline("select 'a\\' ? b', ? from t", values("v"), names("v")));
        Assert.assertEquals("select `a?b`, 'v' from t",
                SqlLiteralInliner.inline("select `a?b`, ? from t", values("v"), names("v")));
        Assert.assertEquals("select \"a?b\", 'v' from t",
                SqlLiteralInliner.inline("select \"a?b\", ? from t", values("v"), names("v")));
    }

    @Test
    public void questionMarksInsideCommentsAreNotPlaceholders() {
        Assert.assertEquals("-- what?\nselect 'v' from t",
                SqlLiteralInliner.inline("-- what?\nselect ? from t", values("v"), names("v")));
        Assert.assertEquals("/* ? */ select 'v' from t",
                SqlLiteralInliner.inline("/* ? */ select ? from t", values("v"), names("v")));
    }

    @Test
    public void countMismatchIsRefusedInsteadOfGuessed() {
        try {
            SqlLiteralInliner.inline("select ?, ? from t", values("only-one"), names("a"));
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("more '?'"));
        }
        try {
            SqlLiteralInliner.inline("select ? from t", values("a", "b"), names("a", "b"));
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("1 '?'"));
        }
    }

    @Test
    public void statementsWithoutParametersPassThrough() {
        Assert.assertEquals("select 1", SqlLiteralInliner.inline("select 1", Collections.emptyList(), null));
        Assert.assertEquals("select 1", SqlLiteralInliner.inline("select 1", null, null));
        Assert.assertNull(SqlLiteralInliner.inline(null, values("v"), null));
    }
}