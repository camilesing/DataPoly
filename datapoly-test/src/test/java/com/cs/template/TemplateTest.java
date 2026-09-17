// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

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
