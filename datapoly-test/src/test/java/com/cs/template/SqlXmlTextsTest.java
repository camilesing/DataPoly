// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.builder.BuilderException;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rules of {@link SqlXmlTexts}: what counts as markup, what gets escaped for the XML parser, and
 * what is left for the parser to report.
 */
public class SqlXmlTextsTest {

    @Test
    public void comparisonsAreWrittenAsInSql() {
        Assert.assertEquals("where a &lt;= 1", SqlXmlTexts.toXmlSafe("where a <= 1"));
        Assert.assertEquals("where a &lt; 1", SqlXmlTexts.toXmlSafe("where a < 1"));
        Assert.assertEquals("where a &lt;> 1", SqlXmlTexts.toXmlSafe("where a <> 1"));
        Assert.assertEquals("where a >= 1", SqlXmlTexts.toXmlSafe("where a >= 1"));
        Assert.assertEquals("where a > 1", SqlXmlTexts.toXmlSafe("where a > 1"));
        // '<' glued to the operand: still a comparison, never a tag
        Assert.assertEquals("where a&lt;b and c>d", SqlXmlTexts.toXmlSafe("where a<b and c>d"));
        Assert.assertEquals("where count&lt;10", SqlXmlTexts.toXmlSafe("where count<10"));
        Assert.assertEquals("where dt&lt;'2020-01-01'", SqlXmlTexts.toXmlSafe("where dt<'2020-01-01'"));
        Assert.assertEquals("where a &lt; (select b)", SqlXmlTexts.toXmlSafe("where a < (select b)"));
    }

    @Test
    public void ampersandIsEscapedUnlessItIsAnEntity() {
        Assert.assertEquals("like 'A&amp;B%'", SqlXmlTexts.toXmlSafe("like 'A&B%'"));
        Assert.assertEquals("a &amp; b", SqlXmlTexts.toXmlSafe("a & b"));
        // a lone '&' is not an entity reference either
        Assert.assertEquals("a &amp;amp b", SqlXmlTexts.toXmlSafe("a &amp b"));
        // existing entity references are the parser's, so they stay untouched
        Assert.assertEquals("a &lt;= b", SqlXmlTexts.toXmlSafe("a &lt;= b"));
        Assert.assertEquals("a &gt;= b", SqlXmlTexts.toXmlSafe("a &gt;= b"));
        Assert.assertEquals("a &amp; b", SqlXmlTexts.toXmlSafe("a &amp; b"));
        Assert.assertEquals("a &#60; b", SqlXmlTexts.toXmlSafe("a &#60; b"));
        Assert.assertEquals("a &#x3c; b", SqlXmlTexts.toXmlSafe("a &#x3c; b"));
        // an unknown entity name is data, not markup
        Assert.assertEquals("a &amp;nbsp; b", SqlXmlTexts.toXmlSafe("a &nbsp; b"));
    }

    @Test
    public void cdataTailIsTheOneBareGreaterThanXmlForbids() {
        Assert.assertEquals("like ']]&gt;'", SqlXmlTexts.toXmlSafe("like ']]>'"));
        Assert.assertEquals("a ]] > b", SqlXmlTexts.toXmlSafe("a ]] > b"));
    }

    @Test
    public void dynamicTagsStayMarkup() {
        Assert.assertEquals("<where><if test='id != null'>id = #{id}</if></where>",
                SqlXmlTexts.toXmlSafe("<where><if test='id != null'>id = #{id}</if></where>"));
        Assert.assertEquals("</if>", SqlXmlTexts.toXmlSafe("</if>"));
        Assert.assertEquals("<bind name='x' value='1'/>", SqlXmlTexts.toXmlSafe("<bind name='x' value='1'/>"));
        Assert.assertEquals("<foreach collection='ids' item='i'>#{i}</foreach>",
                SqlXmlTexts.toXmlSafe("<foreach collection='ids' item='i'>#{i}</foreach>"));
    }

    @Test
    public void operatorsInsideATagAreEscapedInPlace() {
        Assert.assertEquals("<if test='a &lt;= 1 and b >= 2'>x</if>",
                SqlXmlTexts.toXmlSafe("<if test='a <= 1 and b >= 2'>x</if>"));
        Assert.assertEquals("<if test=\"a &lt; b\">x</if>", SqlXmlTexts.toXmlSafe("<if test=\"a < b\">x</if>"));
        // a '>' inside an attribute value does not end the tag, quotes are tracked
        Assert.assertEquals("<if test='a > 1'>x</if>", SqlXmlTexts.toXmlSafe("<if test='a > 1'>x</if>"));
        Assert.assertEquals("<if test=\"name == &quot;x&quot; and a &lt;> 1\">x</if>",
                SqlXmlTexts.toXmlSafe("<if test=\"name == &quot;x&quot; and a <> 1\">x</if>"));
    }

    @Test
    public void tagNamesAreExact() {
        // a longer identifier that merely starts with a tag name is a comparison operand
        Assert.assertEquals("where if_id&lt;if_x", SqlXmlTexts.toXmlSafe("where if_id<if_x"));
        Assert.assertEquals("where a&lt;iffy", SqlXmlTexts.toXmlSafe("where a<iffy"));
        Assert.assertEquals("where a&lt;if(1)", SqlXmlTexts.toXmlSafe("where a<if(1)"));
        // an unknown name with a delimiter is literal text, the database reports it as bad SQL
        Assert.assertEquals("select &lt;iff test='a != null'>x", SqlXmlTexts.toXmlSafe("select <iff test='a != null'>x"));
    }

    @Test
    public void misspelledTagNamesAreReported() {
        try {
            SqlXmlTexts.toXmlSafe("select 1 <if test='a != null'>x</iff>");
            Assert.fail("expected a misspelled closing tag to be reported");
        } catch (BuilderException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("</iff>"));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("foreach"));
        }
        // tag names are case sensitive, like XML
        try {
            SqlXmlTexts.toXmlSafe("select 1 <IF test='a != null'>x</IF>");
            Assert.fail("expected a wrongly cased tag to be reported");
        } catch (BuilderException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("<IF>"));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("<if>"));
        }
    }

    @Test
    public void blocksAreCopiedVerbatim() {
        Assert.assertEquals("where a <![CDATA[ <= ]]> 1", SqlXmlTexts.toXmlSafe("where a <![CDATA[ <= ]]> 1"));
        Assert.assertEquals("<!-- a < b -->select 1", SqlXmlTexts.toXmlSafe("<!-- a < b -->select 1"));
        // a SQL comment is plain text, so it is escaped like any other text and reads the same afterwards
        Assert.assertEquals("-- a &lt; b &amp; c\nselect 1", SqlXmlTexts.toXmlSafe("-- a < b & c\nselect 1"));
    }

    @Test
    public void everyDynamicTagIsMarkupAndHasAHandler() {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("trim", "<trim></trim>");
        samples.put("where", "<where></where>");
        samples.put("set", "<set></set>");
        samples.put("foreach", "<foreach collection='ids' item='i'></foreach>");
        samples.put("if", "<if test='a != null'></if>");
        samples.put("choose", "<choose></choose>");
        samples.put("when", "<when test='a != null'></when>");
        samples.put("otherwise", "<otherwise></otherwise>");
        samples.put("bind", "<bind name='x' value='1'/>");
        Assert.assertEquals(XmlScriptBuilder.DYNAMIC_TAGS, samples.keySet());
        for (Map.Entry<String, String> sample : samples.entrySet()) {
            String markup = sample.getValue();
            Assert.assertEquals(markup, SqlXmlTexts.toXmlSafe(markup));
            // parsing proves the builder registered a handler for the name
            new XmlSqlTemplate("select 1 " + markup);
        }
    }

    @Test
    public void nullAndEmptyArePassedThrough() {
        Assert.assertNull(SqlXmlTexts.toXmlSafe(null));
        Assert.assertEquals("", SqlXmlTexts.toXmlSafe(""));
    }
}