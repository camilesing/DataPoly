// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.junit.*;

import java.util.*;

public class XmlSqlTemplateInlineTest {

    private static Map<String, Object> params(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    public void hashParametersBecomeLiteralsAndLeaveNoBindValues() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where dt = #{dt} and name = #{name}");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dt", "2026-09-18");
        params.put("name", "O'Brien");
        SqlMeta sqlMeta = template.processInlined(params, false);

        Assert.assertEquals("select * from t where dt = '2026-09-18' and name = 'O\\'Brien'", sqlMeta.getSql());
        Assert.assertTrue(sqlMeta.getParameter().isEmpty());
        Assert.assertTrue(sqlMeta.isQuerySQL());
    }

    @Test
    public void definitionsWithoutParametersRenderIdentically() {
        XmlSqlTemplate template = new XmlSqlTemplate("select 1 from t");
        SqlMeta sqlMeta = template.processInlined(Collections.emptyMap(), false);
        Assert.assertEquals("select 1 from t", sqlMeta.getSql());
        Assert.assertTrue(sqlMeta.getParameter().isEmpty());
    }

    @Test
    public void foreachExpandedListsAreInlinedInOrder() {
        XmlSqlTemplate template = new XmlSqlTemplate(
                "select * from t where status in <foreach collection='states' item='s' open='(' separator=',' close=')'>#{s}</foreach>");
        Map<String, Object> params = new HashMap<>();
        params.put("states", Arrays.asList("paid", "O'x"));
        SqlMeta sqlMeta = template.processInlined(params, false);

        String sql = sqlMeta.getSql();
        Assert.assertFalse("no placeholder survives: " + sql, sql.contains("?"));
        Assert.assertTrue(sql, sql.contains("'paid'"));
        Assert.assertTrue(sql, sql.contains("'O\\'x'"));
        Assert.assertTrue("order preserved: " + sql,
                sql.indexOf("'paid'") < sql.indexOf("'O\\'x'"));
        Assert.assertTrue(sqlMeta.getParameter().isEmpty());
    }

    @Test
    public void objectChildrenAreInlined() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where name = #{user.name} and age > #{user.age}");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", "abc");
        user.put("age", 30L);
        SqlMeta sqlMeta = template.processInlined(params("user", user), false);

        Assert.assertEquals("select * from t where name = 'abc' and age > 30", sqlMeta.getSql());
        Assert.assertTrue(sqlMeta.getParameter().isEmpty());
    }

    @Test
    public void dynamicTagsKeepWorking() {
        XmlSqlTemplate template = new XmlSqlTemplate(
                "<if test='name != null'>and name = #{name}</if>");
        SqlMeta present = template.processInlined(params("name", "abc"), false);
        Assert.assertEquals("and name = 'abc'", present.getSql());

        SqlMeta absent = template.processInlined(params("name", null), false);
        Assert.assertEquals("", absent.getSql());
    }

    @Test
    public void dollarSwitchStaysInForce() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t order by ${column}");
        try {
            template.processInlined(params("column", "amount"), false);
            Assert.fail("expected DollarSubstitutionException");
        } catch (DollarSubstitutionException e) {
            // the ban is about the author's ${} tokens, not about the #{} style
        }
        SqlMeta sqlMeta = template.processInlined(params("column", "amount"), true);
        Assert.assertEquals("select * from t order by amount", sqlMeta.getSql());
    }

    @Test
    public void dollarSubstitutionAndInliningCoexist() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from ${table} where dt = #{dt}");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("table", "t_order");
        params.put("dt", "2026-09-18");
        SqlMeta sqlMeta = template.processInlined(params, true);

        // ${} still splices text verbatim; only #{} values are quoted and escaped
        Assert.assertEquals("select * from t_order where dt = '2026-09-18'", sqlMeta.getSql());
        Assert.assertTrue(sqlMeta.getParameter().isEmpty());
    }

    @Test
    public void boundRenderingIsUnchanged() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where dt = #{dt}");
        SqlMeta sqlMeta = template.process(params("dt", "2026-09-18"), false);
        Assert.assertEquals("select * from t where dt = ?", sqlMeta.getSql());
        Assert.assertEquals(1, sqlMeta.getParameter().size());
    }

    @Test
    public void aLiteralQuestionMarkIsRefusedInsteadOfMisplacingValues() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where flag = ? and dt = #{dt}");
        try {
            template.processInlined(params("dt", "2026-09-18"), false);
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("'?'"));
        }
    }

    @Test
    public void parametersBoundAsWholeCollectionsAreRefused() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where id = #{ids}");
        try {
            template.processInlined(params("ids", Arrays.asList(1L, 2L)), false);
            Assert.fail("expected ParameterInliningException");
        } catch (ParameterInliningException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("ids"));
        }
    }
}