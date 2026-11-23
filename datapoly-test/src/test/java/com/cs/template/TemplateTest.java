// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.junit.*;

import java.util.*;

public class TemplateTest {

    @Test
    public void testIf() {
        String content = "select * from user where <if test='id != null ' > id  = #{id} </if>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Assert.assertTrue(parser.getParameterNames().containsKey("id"));

        HashMap<String, Object> map = new HashMap<>();
        map.put("id", "11");
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("= ?"));
        Assert.assertEquals(Arrays.asList((Object) "11"), process.getParameter());
    }

    @Test
    public void testWhere() {
        String content = "select * from user <where> <if test='id != null ' > and id  = #{id} </if>  <if test=' name != null' >name =#{name}</if> </where>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Assert.assertTrue(parser.getParameterNames().containsKey("id"));
        Assert.assertTrue(parser.getParameterNames().containsKey("name"));

        HashMap<String, Object> map = new HashMap<>();
        //map.put("name", "join");
        SqlMeta process = parser.process(map);
        // Neither if matches: no WHERE fragment, no parameters
        Assert.assertTrue(process.getParameter().isEmpty());
        Assert.assertFalse(process.getSql().toUpperCase().contains("WHERE"));
    }


    @Test
    public void testSet() {
        String content = "update user <set> <if test='id != null '> id = #{id} ,</if><if test='name != null '> name = #{name} ,</if> </set> ";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Assert.assertEquals(2, parser.getParameterNames().size());

        HashMap<String, Object> map = new HashMap<>();
        map.put("id", "123");
        map.put("name", "join");
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("id = ?"));
        Assert.assertTrue(process.getSql().contains("name = ?"));
        Assert.assertEquals(Arrays.asList((Object) "123", "join"), process.getParameter());
    }


    @Test
    public void testChoose() {
        String content = "select  * from user <where><choose><when test=' id!= null '> and id = #{id} </when><when test=' name!= null and name.length()>0 '> and name = #{name} </when></choose> </where>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Assert.assertTrue(parser.getParameterNames().containsKey("id"));
        Assert.assertTrue(parser.getParameterNames().containsKey("name"));

        HashMap<String, Object> map = new HashMap<>();
        //map.put("id", "123");
        map.put("name", "join");
        SqlMeta process = parser.process(map);
        // Hits the second when branch
        Assert.assertFalse(process.getSql().contains("id ="));
        Assert.assertTrue(process.getSql().contains("name = ?"));
        Assert.assertEquals(Arrays.asList((Object) "join"), process.getParameter());
    }

    @Test
    public void testForEach() {
        String content = "select  * from user <where> id in <foreach item=\"item\" index=\"index\" collection=\"list\"    open=\"(\" separator=\",\" close=\")\">   ${item}   ${index}  </foreach></where>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Assert.assertTrue(parser.getParameterNames().containsKey("list"));

        HashMap<String, Object> map = new HashMap<>();
        map.put("id", "123");
        map.put("name", "join");
        HashMap<String, Object> map2 = new HashMap<>();
        map2.put("11", "111");
        map2.put("22", "222");
        map.put("list", map2);
        SqlMeta process = parser.process(map);
        // ${} is textual substitution and produces no placeholders; the parentheses around in (...) and both groups should appear
        Assert.assertTrue(process.getParameter().isEmpty());
        Assert.assertTrue(process.getSql().contains("in ("));
        Assert.assertTrue(process.getSql().contains("111"));
        Assert.assertTrue(process.getSql().contains("222"));
        Assert.assertTrue(process.getSql().trim().endsWith(")"));
    }

    @Test
    public void testLT() {
        String content = "select * from user where <if test='id != null ' > id  <![CDATA[ < ]]> #{id} </if>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Assert.assertTrue(parser.getParameterNames().containsKey("id"));

        HashMap<String, Object> map = new HashMap<>();
        map.put("id", "11");
        SqlMeta process = parser.process(map);
        // CDATA-wrapped less-than sign should remain in the SQL
        Assert.assertTrue(process.getSql().contains("<"));
        Assert.assertEquals(Arrays.asList((Object) "11"), process.getParameter());
    }

    @Test
    public void testTrim() {
        String content = "SELECT * from employees.employees \n"
                + "<trim prefix =\"WHERE\" prefixOverrides=\"AND | OR\">\n"
                + "  <if test=\"empNo != null\">\n"
                + "    AND emp_no = #{empNo}\n"
                + "  </if>\n"
                + "  <if test=\"firstName != null\">\n"
                + "    AND first_name like concat('%', #{firstName}, '%')\n"
                + "  </if>\n"
                + "  <if test=\"lastName != null \">\n"
                + "    AND last_name like concat('%', #{lastName}, '%')\n"
                + "  </if>\n"
                + " </trim>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);
        Map<String, Boolean> names = parser.getParameterNames();
        Assert.assertTrue(names.containsKey("empNo"));
        Assert.assertTrue(names.containsKey("firstName"));
        Assert.assertTrue(names.containsKey("lastName"));
    }

    @Test
    public void parseParams() {
        String content = "SELECT count(DISTINCT datasource_uuid,database_name,table_name) \n"
                + "FROM t_test_record \n"
                + "WHERE is_sensitive=#{status} AND is_confirmed='1' AND is_deleted='0' \n"
                + "<choose>\n"
                + "  <when test=\"dsUuids!=null and dsUuids.size ==1 \">\n"
                + "AND datasource_uuid =#{dsUuids[0]} \n"
                + "  </when>\n"
                + "  <when test=\"dsUuids!=null and dsUuids.size > 1 \">\n"
                + "AND datasource_uuid in \n"
                + "    <foreach item='item' collection='dsUuids' separator=',' open='(' close=')' index=''> \n"
                + "     #{item} \n"
                + "    </foreach> \n"
                + "  </when>\n"
                + "</choose>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);
        Map<String, Boolean> names = parser.getParameterNames();
        Assert.assertTrue(names.containsKey("status"));
        Assert.assertTrue(names.containsKey("dsUuids"));
        // #{item} inside foreach is merged under the collection name and does not appear separately
        Assert.assertFalse(names.containsKey("item"));
    }

    @Test
    public void foreachWithoutCollectionOrItemIsRejectedAsABuilderException() {
        // Regression: a missing attribute surfaced as an NPE — not as the sql.template.invalid error the
        // save path maps — and an item name was used as a regular expression
        assertInvalidTemplate("<foreach open='(' close=')'>#{id}</foreach>");
        assertInvalidTemplate("<foreach collection='ids' open='(' close=')'>#{id}</foreach>");

        // the item name is matched literally: a regex metacharacter in it is no longer a syntax error
        Map<String, Boolean> names = new XmlSqlTemplate(
                "<foreach collection='ids' item='id[' open='(' close=')'>#{id[.x}</foreach>").getParameterNames();
        Assert.assertTrue(String.valueOf(names), names.containsKey("ids.x"));
    }

    private static void assertInvalidTemplate(String template) {
        try {
            new XmlSqlTemplate(template).getParameterNames();
            Assert.fail("expected the template to be rejected: " + template);
        } catch (BuilderException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("foreach"));
        }
    }

    @Test
    public void testObject() {
        String xmlSql = " insert into `employees` "
                + " (emp_no,birth_date,first_name,last_name,gender,hire_date)"
                + " values "
                + "<foreach collection='records' item='record' separator=','>"
                + " ( #{record.empNo}, #{record.birthDate}, #{record.firstName}, #{record.lastName}, #{record.gender}, #{record.hireDate})"
                + "</foreach>";

        XmlSqlTemplate sqlParser = new XmlSqlTemplate(xmlSql);
        Map<String, Boolean> paramNames = sqlParser.getParameterNames();
        Assert.assertTrue(paramNames.containsKey("records"));
        // Inside foreach, #{record.xxx} item names are renamed to the collection name
        Assert.assertTrue(paramNames.containsKey("records.empNo"));

        Map<String, Object> record1 = new HashMap<>();
        record1.put("empNo", 11);
        record1.put("birthDate", new Date());
        record1.put("firstName", "tom1");
        record1.put("lastName", "cat1");
        record1.put("gender", "F");
        record1.put("hireDate", new Date());
        Map<String, Object> record2 = new HashMap<>();
        record2.put("empNo", 22);
        record2.put("birthDate", new Date());
        record2.put("firstName", "tom2");
        record2.put("lastName", "cat2");
        record2.put("gender", "F");
        record2.put("hireDate", new Date());
        List<Map<String, Object>> employees = Arrays.asList(record1, record2);

        Map<String, Object> params = new HashMap<>();
        params.put("records", employees);
        SqlMeta sqlData = sqlParser.process(params);
        // 2 records x 6 fields = 12 placeholders
        Assert.assertEquals(12, sqlData.getParameter().size());
        Assert.assertTrue(sqlData.getParameter().contains(11));
        Assert.assertTrue(sqlData.getParameter().contains(22));
        Assert.assertEquals(12, countOccurrences(sqlData.getSql(), '?'));
    }

    @Test
    public void testObjFiled() {
        String sql = "SELECT * from employees.departments WHERE dept_no=#{obj.deptNo}";
        XmlSqlTemplate template = new XmlSqlTemplate(sql);
        Map<String, Boolean> names = template.getParameterNames();
        // Plain #{} nested reference: both levels are non-collection references (false)
        Assert.assertEquals(Boolean.FALSE, names.get("obj"));
        Assert.assertEquals(Boolean.FALSE, names.get("obj.deptNo"));

        Map<String, Object> obj = new HashMap<>();
        obj.put("deptNo", "d001");

        Map<String, Object> params = new HashMap<>();
        params.put("obj", obj);
        SqlMeta sqlMeta = template.process(params);
        Assert.assertEquals(Arrays.asList((Object) "d001"), sqlMeta.getParameter());
        Assert.assertTrue(sqlMeta.getSql().contains("dept_no=?"));
    }

    @Test
    public void testObjArrayField() {
        String sql = "SELECT * from employees.departments WHERE dept_no in \n"
                + "<foreach open=\"(\" close=\")\" collection=\"obj.deptNo\" separator=\",\" item=\"item\" index=\"index\">"
                + "#{item}"
                + "</foreach>";
        XmlSqlTemplate template = new XmlSqlTemplate(sql);
        Assert.assertTrue(template.getParameterNames().containsKey("obj.deptNo"));

        Map<String, Object> obj = new HashMap<>();
        obj.put("deptNo", Arrays.asList("d001", "d002"));

        Map<String, Object> params = new HashMap<>();
        params.put("obj", obj);
        SqlMeta sqlMeta = template.process(params);
        Assert.assertEquals(Arrays.asList((Object) "d001", "d002"), sqlMeta.getParameter());
        Assert.assertEquals(2, countOccurrences(sqlMeta.getSql(), '?'));
    }

    @Test
    public void testObjNotRequiredField() {
        String sql = "SELECT * from employees.departments "
                + "<if test='obj.deptNo != null and obj.deptNo.size() > 0'> "
                + " WHERE dept_no in "
                + " <foreach open=\"(\" close=\")\" collection=\"obj.deptNo\" separator=\",\" item=\"item\" index=\"index\"> "
                + " #{item} "
                + " </foreach> "
                + "</if>";
        XmlSqlTemplate template = new XmlSqlTemplate(sql);
        Map<String, Boolean> names = template.getParameterNames();
        // Static foreach collection collection still registers obj and obj.deptNo (true = collection reference),
        // but the outer <if> guard means actual requests may omit deptNo
        Assert.assertTrue(names.containsKey("obj"));
        Assert.assertEquals(Boolean.TRUE, names.get("obj.deptNo"));

        Map<String, Object> obj = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("obj", obj);
        SqlMeta sqlMeta = template.process(params);
        Assert.assertTrue(sqlMeta.getParameter().isEmpty());
        Assert.assertFalse(sqlMeta.getSql().toUpperCase().contains("WHERE"));
    }

    @Test
    public void testRawOperatorsInPlainSql() {
        // Plain SQL as an author writes it: comparison operators need no XML escape
        String content = "select order_id, amount from t_order "
                + "where dt >= #{dt} and amount <= #{amount} and status <> #{status} and name like 'A&B%'";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Boolean> names = parser.getParameterNames();
        Assert.assertTrue(names.containsKey("dt"));
        Assert.assertTrue(names.containsKey("amount"));
        Assert.assertTrue(names.containsKey("status"));

        Map<String, Object> map = new HashMap<>();
        map.put("dt", "2026-01-01");
        map.put("amount", 100);
        map.put("status", "1");
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("dt >= ?"));
        Assert.assertTrue(process.getSql().contains("amount <= ?"));
        Assert.assertTrue(process.getSql().contains("status <> ?"));
        Assert.assertTrue(process.getSql().contains("like 'A&B%'"));
        Assert.assertEquals(Arrays.asList((Object) "2026-01-01", 100, "1"), process.getParameter());
    }

    @Test
    public void testRawLessThan() {
        String content = "select * from t_order where amount < #{amount}";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Object> map = new HashMap<>();
        map.put("amount", 100);
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("amount < ?"));
        Assert.assertEquals(Arrays.asList((Object) 100), process.getParameter());
    }

    @Test
    public void testRawOperatorInsideIfTestAttribute() {
        String content = "select * from t_user <where>"
                + "<if test='age <= 18 or age >= 60'> and vip = #{vip} </if>"
                + "</where>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);
        Assert.assertTrue(parser.getParameterNames().containsKey("vip"));

        Map<String, Object> map = new HashMap<>();
        map.put("age", 70);
        map.put("vip", "Y");
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("vip = ?"));
        Assert.assertEquals(Arrays.asList((Object) "Y"), process.getParameter());
    }

    @Test
    public void testRawOperatorDeepInMultilineSql() {
        // Reported failure shape: SAXParseException "The content of elements must consist of
        // well-formed character data or markup" once '<=' sat deep into a long statement
        String content = "select a, b, c\n"
                + "from t_order\n"
                + "where 1 = 1\n"
                + "<if test='dt != null'>\n"
                + "  and dt >= #{dt}\n"
                + "</if>\n"
                + "<if test=\"amount != null and amount >= 0\">\n"
                + "  and status in\n"
                + "    <foreach collection='status' item='item' open='(' close=')' separator=','>\n"
                + "      #{item}\n"
                + "    </foreach>\n"
                + "  and amount <= #{amount}\n"
                + "</if>\n"
                + "order by a\n";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Object> map = new HashMap<>();
        map.put("dt", "2026-01-01");
        map.put("amount", 100);
        map.put("status", Arrays.asList("1", "2"));
        SqlMeta process = parser.process(map);
        // both branches were taken: the '<if test="… amount >= 0">' guard on its own raw operator,
        // and the '<=' in the body
        Assert.assertTrue(process.getSql().contains("dt >= ?"));
        Assert.assertTrue(process.getSql().contains("amount <= ?"));
        Assert.assertEquals(4, countOccurrences(process.getSql(), '?'));
        Assert.assertEquals(Arrays.asList((Object) "2026-01-01", "1", "2", 100), process.getParameter());
    }

    @Test
    public void testEscapedOperatorsFromExistingConfigs() {
        // Configs saved before this change: entities still resolve to the same SQL
        String content = "select * from t_order "
                + "where amount &lt;= #{amount} and dt &gt;= #{dt} and name like 'A&amp;B%'";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Object> map = new HashMap<>();
        map.put("amount", 100);
        map.put("dt", "2026-01-01");
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("amount <= ?"));
        Assert.assertTrue(process.getSql().contains("dt >= ?"));
        Assert.assertTrue(process.getSql().contains("like 'A&B%'"));
        Assert.assertEquals(Arrays.asList((Object) 100, "2026-01-01"), process.getParameter());
    }

    @Test
    public void testMyBatisAttributeSyntaxKeepsThePlainName() {
        // #{flag,javaType=int}: the stored raw token carries the attributes, the exposed name is
        // only the property before the comma
        String content = "select * from t where flag = #{flag,javaType=int}";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);
        Map<String, Boolean> names = parser.getParameterNames();
        Assert.assertTrue(names.containsKey("flag"));

        Map<String, Object> map = new HashMap<>();
        map.put("flag", 1);
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("flag = ?"));
        Assert.assertEquals(Arrays.asList((Object) 1), process.getParameter());
    }

    /** A bean, so a dotted reference resolves to an object that is not a Map. */
    public static class Department {

        private final String deptNo;

        public Department(String deptNo) {
            this.deptNo = deptNo;
        }

        public String getDeptNo() {
            return deptNo;
        }
    }

    @Test
    public void testDottedNameOnNonMapBindsTheWholeObject() {
        String sql = "SELECT * from employees.departments WHERE dept_no=#{obj.deptNo}";
        XmlSqlTemplate template = new XmlSqlTemplate(sql);
        Department department = new Department("d001");

        Map<String, Object> params = new HashMap<>();
        params.put("obj", department);
        SqlMeta sqlMeta = template.process(params);
        Assert.assertTrue(sqlMeta.getSql().contains("dept_no=?"));
        // a non-Map object has no member to pull out: the object itself is what gets bound
        Assert.assertEquals(Arrays.asList((Object) department), sqlMeta.getParameter());
    }

    @Test
    public void testXmlCommentNodesAreIgnored() {
        String content = "select 1 <!-- cache:none --> from t";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);
        Assert.assertTrue(parser.getParameterNames().isEmpty());
        SqlMeta process = parser.process(new HashMap<>());
        // the comment node contributes nothing; the two text nodes around it are kept
        Assert.assertEquals("select 1  from t", process.getSql());
    }

    @Test
    public void testBind() {
        String content = "select * from t <where>"
                + "<bind name=\"pattern\" value=\"'%' + name + '%'\"/> name like #{pattern}"
                + "</where>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Boolean> names = parser.getParameterNames();
        // the bind's value expression is not scanned, only the #{pattern} placeholder in the body
        Assert.assertEquals(Collections.singletonMap("pattern", Boolean.FALSE), names);

        Map<String, Object> map = new HashMap<>();
        map.put("name", "abc");
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("name like ?"));
        Assert.assertEquals(Arrays.asList((Object) "%abc%"), process.getParameter());
    }

    @Test
    public void testChooseOtherwise() {
        String content = "select * from user <where><choose>"
                + "<when test='id != null'> and id = #{id} </when>"
                + "<otherwise> and id is null </otherwise>"
                + "</choose></where>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Object> map = new HashMap<>();
        SqlMeta process = parser.process(map);
        Assert.assertFalse(process.getSql().contains("id ="));
        Assert.assertTrue(process.getSql().contains("id is null"));
        Assert.assertTrue(process.getSql().toUpperCase().contains("WHERE"));
    }

    @Test
    public void testNonBranchElementsInsideChooseAreIgnored() {
        // a <where> inside <choose> is not a choose arm: only when/otherwise children count
        String content = "select * from user <choose>"
                + "<where> id = 1 </where>"
                + "<when test='id != null'> and id = #{id} </when>"
                + "</choose>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);

        Map<String, Object> map = new HashMap<>();
        map.put("id", 5);
        SqlMeta process = parser.process(map);
        Assert.assertTrue(process.getSql().contains("id = ?"));
        Assert.assertFalse(process.getSql().toUpperCase().contains("WHERE"));
    }

    @Test
    public void testTwoOtherwiseBranchesAreRefused() {
        XmlSqlTemplate parser = new XmlSqlTemplate(
                "<choose><otherwise>a</otherwise><otherwise>b</otherwise></choose>");
        try {
            parser.getParameterNames();
            Assert.fail("expected BuilderException");
        } catch (BuilderException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Too many default"));
        }
    }

    @Test
    public void testEmptyDollarTokenInsideForeachRegistersNothingExtra() {
        // '${}' names no parameter; only the collection is exposed
        String content = "select * from t where id in "
                + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>${}</foreach>";
        XmlSqlTemplate parser = new XmlSqlTemplate(content);
        Map<String, Boolean> names = parser.getParameterNames();
        Assert.assertTrue(names.containsKey("ids"));
        Assert.assertEquals(1, names.size());
    }

    @Test
    public void unknownElementsAreRefusedWithTheirName() {
        // reachable only through a hand-built document: toXmlSafe escapes unknown opening tags in
        // authored SQL long before parseDynamicTags sees them
        XNode node = new XPathParser("<script><froeach/></script>").evalNodes("script").get(0);
        try {
            new XmlScriptBuilder(new Configuration(), node).parseScriptNode(new HashMap<>());
            Assert.fail("expected BuilderException");
        } catch (BuilderException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("froeach"));
        }
    }

    @Test
    public void nonTextNonElementNodesAreSkipped() {
        // a processing instruction contributes nothing; authored SQL cannot produce one (toXmlSafe
        // escapes '<?'), and XPathParser drops comments, so only a hand-built document gets here
        XNode node = new XPathParser("<script><?target data?></script>").evalNodes("script").get(0);
        org.apache.ibatis.mapping.SqlSource sqlSource =
                new XmlScriptBuilder(new Configuration(), node).parseScriptNode(new HashMap<>());
        Assert.assertEquals("", sqlSource.getBoundSql(null).getSql());
    }

    private static int countOccurrences(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }
}
