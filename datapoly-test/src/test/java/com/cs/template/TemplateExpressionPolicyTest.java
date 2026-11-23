// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.builder.BuilderException;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Template expressions ({@code test}, {@code bind value}, {@code foreach collection} and {@code ${}}) are
 * evaluated by OGNL with its whole vocabulary unless the builder says otherwise: static references,
 * constructors and reflective access would let an API author read files and environment variables on
 * the executor host. They are rejected when the template is parsed — at save time already.
 */
public class TemplateExpressionPolicyTest {

    private static void assertRejectedAtSave(String template) {
        try {
            new XmlSqlTemplate(template).getParameterNames();
            fail("expected the template to be rejected: " + template);
        } catch (BuilderException expected) {
            assertFalse(expected.getMessage(), expected.getMessage().isEmpty());
        }
    }

    private static void assertRejectedAtRender(String template) {
        try {
            new XmlSqlTemplate(template).process(new HashMap<>());
            fail("expected the template to be rejected: " + template);
        } catch (BuilderException expected) {
            // rejected while building the statement, before anything is evaluated
        }
    }

    @Test
    public void staticReferencesAreRejected() {
        assertRejectedAtSave("<bind name='q' value=\"@java.lang.System@getenv('HOME')\"/> select #{q}");
        assertRejectedAtSave("<bind name='q' value=\"@java.lang.System@getProperty('user.dir')\"/> select #{q}");
        assertRejectedAtSave("<if test=\"@java.lang.Boolean@TRUE\">select 1</if>");
        assertRejectedAtSave("<foreach collection=\"@java.lang.System@getenv().keySet()\" item='k'>#{k}</foreach>");
        assertRejectedAtRender("<bind name='q' value=\"@java.lang.Thread@currentThread().getName()\"/> select #{q}");
    }

    @Test
    public void constructorsAreRejected() {
        assertRejectedAtSave(
                "<bind name='q' value=\"new java.io.FileInputStream('/etc/hosts').readAllBytes().length\"/> select #{q}");
        assertRejectedAtSave("<bind name='q' value=\"new java.io.File('/etc').list()[0]\"/> select #{q}");
        assertRejectedAtSave("<if test=\"new java.io.File('/etc/hosts').exists()\">select 1</if>");
    }

    @Test
    public void reflectiveAccessIsRejected() {
        assertRejectedAtSave("<if test=\"name.class.classLoader != null\">select 1</if>");
        assertRejectedAtSave("<if test=\"name['class'] != null\">select 1</if>");
        assertRejectedAtSave("<if test=\"name.getClass().forName('java.lang.Runtime') != null\">select 1</if>");
        assertRejectedAtSave("<bind name='q' value=\"name.getClass().getName()\"/> select #{q}");
    }

    @Test
    public void evalIsRejected() {
        assertRejectedAtSave("<bind name='q' value=\"('1' + '+1')(#this)\"/> select #{q}");
    }

    @Test
    public void dollarTokensFollowTheSamePolicy() {
        assertRejectedAtSave("select ${@java.lang.System@getenv('HOME')}");
        assertRejectedAtSave("select ${name.getClass().getName()}");
    }

    @Test
    public void everyDayExpressionsStillWork() {
        String template = "select * from t <where>"
                + "<if test=\"name != null and name != ''\">and name like #{name}</if>"
                + "<if test=\"type == 1 or type == 2\">and type = #{type}</if>"
                + "<if test=\"email != null and email.contains(&quot;@&quot;)\">and email = #{email}</if>"
                + "<if test=\"ids != null and ids.size() > 0\">"
                + "<foreach collection='ids' item='id' open='and id in (' separator=',' close=')'>#{id}</foreach>"
                + "</if>"
                + "</where> order by ${order}";
        XmlSqlTemplate xml = new XmlSqlTemplate(template);
        assertTrue(xml.getParameterNames().keySet()
                .containsAll(Arrays.asList("name", "type", "email", "ids", "order")));

        Map<String, Object> params = new HashMap<>();
        params.put("name", "abc");
        params.put("type", 1);
        params.put("email", "someone@example.com");
        params.put("ids", Arrays.asList(1L, 2L));
        params.put("order", "id desc");
        SqlMeta meta = xml.process(params);

        assertTrue(meta.getSql(), meta.getSql().contains("name like ?"));
        assertTrue(meta.getSql(), meta.getSql().contains("and type = ?"));
        assertTrue(meta.getSql(), meta.getSql().contains("and id in ("));
        assertTrue(meta.getSql(), meta.getSql().contains("order by id desc"));
        assertEquals(Arrays.asList("abc", 1, "someone@example.com", 1L, 2L), meta.getParameter());
    }

    @Test
    public void bindWithLiteralArithmeticStillWorks() {
        String template = "<bind name=\"pattern\" value=\"'%' + keyword + '%'\"/>select * from t where name like #{pattern}";
        SqlMeta meta = new XmlSqlTemplate(template).process(Collections.singletonMap("keyword", "abc"));

        assertEquals(Arrays.asList("%abc%"), meta.getParameter());
    }
}
