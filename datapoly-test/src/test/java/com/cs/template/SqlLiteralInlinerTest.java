// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.junit.*;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    @Test
    public void minusAndSlashAreOnlyCommentsWhenDoubledOrStarred() {
        Assert.assertEquals("select 5 - 1", SqlLiteralInliner.inline("select 5 - ?", values(1), names("a")));
        Assert.assertEquals("select 6 / 2", SqlLiteralInliner.inline("select 6 / ?", values(2), names("a")));
        // a trailing '-' or '/' is one more ordinary character; the placeholder it swallows is missed
        assertRefused("select 5 -", values(1), names("a"), "0 '?'");
        assertRefused("select 5 /", values(1), names("a"), "0 '?'");
    }

    @Test
    public void everyIntegerWidthRendersAsItself() {
        Assert.assertEquals("123456789012345", SqlLiteralInliner.literal(new BigInteger("123456789012345"), "p"));
        Assert.assertEquals("7", SqlLiteralInliner.literal((byte) 7, "p"));
        Assert.assertEquals("300", SqlLiteralInliner.literal((short) 300, "p"));
        Assert.assertEquals("42", SqlLiteralInliner.literal(42, "p"));
        Assert.assertEquals("1.5", SqlLiteralInliner.literal(1.5F, "p"));
    }

    @Test
    public void infiniteFloatsAreRefusedLikeNaN() {
        try {
            SqlLiteralInliner.literal(Float.POSITIVE_INFINITY, "ratio");
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("ratio"));
        }
    }

    @Test
    public void nulCharactersInAValueAreRefused() {
        try {
            SqlLiteralInliner.literal("a\0b", "payload");
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("NUL"));
        }
    }

    @Test
    public void missingNamesFallBackToPositionalNumbers() {
        // a null name list renders fine; names only appear in diagnostics
        Assert.assertEquals("select 'v'", SqlLiteralInliner.inline("select ?", values("v"), null));
        Assert.assertEquals("select 'a', 'b'",
                SqlLiteralInliner.inline("select ?, ?", values("a", "b"), names("only-first")));
        Assert.assertEquals("select 'a', 'b'",
                SqlLiteralInliner.inline("select ?, ?", values("a", "b"), names("x", null)));
        // the positional fallback is what the error message shows
        try {
            SqlLiteralInliner.inline("select ?, ?", values("a", Arrays.asList(1)), names("first"));
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("#2"));
        }
    }

    @Test
    public void unbalancedQuotesSwallowTheirQuestionMarks() {
        // a quote that never closes takes the rest with it, so no placeholder is found
        assertRefused("select 'a", values("v"), names("v"), "0 '?'");
        assertRefused("select 'a", values("v"), names("v"), "unbalanced");
        // a closing quote as the very last character
        assertRefused("select 'a'", values("v"), names("v"), "0 '?'");
        // a backslash as the very last character cannot escape anything
        assertRefused("select 'a\\", values("v"), names("v"), "0 '?'");
    }

    @Test
    public void commentsRunningPastTheEndSwallowTheirQuestionMarks() {
        assertRefused("-- tail ?", values("v"), names("v"), "0 '?'");
        assertRefused("/* open ?", values("v"), names("v"), "0 '?'");
    }

    @Test
    public void blockCommentEdgeCharactersStayLiteral() {
        // '*' inside a block comment that is not the closing pair
        Assert.assertEquals("/* a*b ? */ select 'v' from t",
                SqlLiteralInliner.inline("/* a*b ? */ select ? from t", values("v"), names("v")));
        // '*' as the very last character: no '/' can follow
        Assert.assertEquals("select 'v' /* *", SqlLiteralInliner.inline("select ? /* *", values("v"), names("v")));
    }

    /** Inlining that must fail; the message is asserted to contain {@code fragment}. */
    private static void assertRefused(String sql, List<Object> values, List<String> names, String fragment) {
        try {
            SqlLiteralInliner.inline(sql, values, names);
        } catch (ParameterInliningException e) {
            Assert.assertTrue("message: " + e.getMessage(), e.getMessage().contains(fragment));
            return;
        }
        throw new AssertionError("expected ParameterInliningException for: " + sql);
    }
}