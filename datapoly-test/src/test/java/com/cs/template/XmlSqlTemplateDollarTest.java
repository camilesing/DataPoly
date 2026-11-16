// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.junit.*;

import java.util.*;

public class XmlSqlTemplateDollarTest {

    private static Map<String, Object> params(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    public void containsDollarTokenDetectsSubstitutionOnly() {
        Assert.assertTrue(XmlSqlTemplate.containsDollarToken("select * from t where name = '${name}'"));
        Assert.assertTrue(XmlSqlTemplate.containsDollarToken("order by ${column}"));
        Assert.assertFalse(XmlSqlTemplate.containsDollarToken("select * from t where name = #{name}"));
        Assert.assertFalse(XmlSqlTemplate.containsDollarToken("select 1"));
        Assert.assertFalse(XmlSqlTemplate.containsDollarToken(""));
        Assert.assertFalse(XmlSqlTemplate.containsDollarToken(null));
        Assert.assertFalse("未闭合 token 不算替换", XmlSqlTemplate.containsDollarToken("select '${name'"));
    }

    @Test
    public void processAllowedByDefault() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where name = '${name}'");
        Map<String, Object> params = params("name", "abc");
        SqlMeta sqlMeta = template.process(params);
        Assert.assertEquals("select * from t where name = 'abc'", sqlMeta.getSql());
    }

    @Test
    public void processForbiddenRejectsDollar() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where name = '${name}'");
        try {
            template.process(params("name", "abc"), false);
            Assert.fail("expected DollarSubstitutionException");
        } catch (DollarSubstitutionException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().toLowerCase().contains("dollar"));
        }
    }

    @Test
    public void processForbiddenKeepsHashParameterization() {
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where name = #{name}");
        SqlMeta sqlMeta = template.process(params("name", "abc"), false);
        Assert.assertEquals("select * from t where name = ?", sqlMeta.getSql());
        Assert.assertEquals(1, sqlMeta.getParameter().size());
        Assert.assertEquals("abc", sqlMeta.getParameter().get(0));
    }

    @Test
    public void dynamicXmlStillForbiddenWhenDollarPresent() {
        XmlSqlTemplate template = new XmlSqlTemplate(
                "<if test='name != null'>and name = '${name}'</if>");
        try {
            template.process(params("name", "abc"), false);
            Assert.fail("expected DollarSubstitutionException");
        } catch (DollarSubstitutionException e) {
            // ${} inside dynamic nodes is intercepted too
        }
    }

    @Test
    public void getParameterNamesUnaffectedByGuard() {
        // Parameter parsing for the save page is unaffected by the ban (still parses ${} parameter names)
        XmlSqlTemplate template = new XmlSqlTemplate("select * from t where name = '${name}'");
        Assert.assertTrue(template.getParameterNames().containsKey("name"));
    }

    @Test
    public void isQuerySqlStripsLeadingCommentsAndParens() {
        Assert.assertTrue(new SqlMeta("  (select 1)", null).isQuerySQL());
        Assert.assertTrue(new SqlMeta("-- comment\nselect 1", null).isQuerySQL());
        Assert.assertTrue(new SqlMeta("/* hint */ select 1", null).isQuerySQL());
        Assert.assertTrue(new SqlMeta("with c as (select 1) select * from c", null).isQuerySQL());
        Assert.assertFalse(new SqlMeta("update t set a = 1", null).isQuerySQL());
        Assert.assertFalse(new SqlMeta("-- only comment", null).isQuerySQL());
        Assert.assertFalse(new SqlMeta("/* unterminated", null).isQuerySQL());
    }

    @Test
    public void isQuerySqlTreatsLoneMinusAndSlashAsText() {
        // whitespace between stripped characters is skipped too
        Assert.assertTrue(new SqlMeta("( select 1)", null).isQuerySQL());
        // a '-' that is not a line comment stops the stripping
        Assert.assertFalse(new SqlMeta("(-5) select 1", null).isQuerySQL());
        Assert.assertFalse(new SqlMeta("(-", null).isQuerySQL());
        // a '/' that is not a block comment stops the stripping
        Assert.assertFalse(new SqlMeta("(/ x) select 1", null).isQuerySQL());
        Assert.assertFalse(new SqlMeta("(/", null).isQuerySQL());
        // nothing but strippable characters leaves an empty statement, which is no query
        Assert.assertFalse(new SqlMeta("(", null).isQuerySQL());
    }

    @Test
    public void sqlMetaSettersAndToString() {
        SqlMeta sqlMeta = new SqlMeta("  select 1  ", null);
        Assert.assertEquals("select 1", sqlMeta.getSql());
        sqlMeta.setSql("  update t set a = 1  ");
        Assert.assertEquals("update t set a = 1", sqlMeta.getSql());
        List<Object> parameters = new ArrayList<>(Arrays.asList("x"));
        sqlMeta.setParameter(parameters);
        Assert.assertEquals(parameters, sqlMeta.getParameter());
        Assert.assertTrue(sqlMeta.toString(), sqlMeta.toString().contains("update t set a = 1"));
    }
}
