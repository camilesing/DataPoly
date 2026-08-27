// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.*;
import org.apache.ibatis.session.Configuration;

import java.util.*;
import java.util.regex.*;

public class XmlSqlTemplate {

    private static final Pattern pattern = Pattern.compile("\\([^()]*\\)|\\[[^\\[\\]]*\\]|\\{[^{}]*}");

    /**
     * Shared Configuration: used only by XmlScriptBuilder to parse dynamic SQL (read-only; MyBatis Configuration is thread-safe),
     * avoiding repeated construction per template instance (its TypeHandlerRegistry init is not cheap)
     */
    private static final Configuration configuration = createSharedConfiguration();

    private XNode root;
    private String xmlSql;

    public XmlSqlTemplate(String xmlSql) {
        this.xmlSql = xmlSql;
        this.root = parseXml(xmlSql);
    }

    /**
     * Detects whether the SQL text contains a ${} literal substitution token (S3): same token semantics as
     * MyBatis TextSqlNode's dynamic check (GenericTokenParser "${"/"}"); does not affect #{} or plain text.
     */
    public static boolean containsDollarToken(String sqlText) {
        if (null == sqlText || sqlText.isEmpty()) {
            return false;
        }
        boolean[] found = new boolean[1];
        new GenericTokenParser("${", "}", name -> {
            found[0] = true;
            return name;
        }).parse(sqlText);
        return found[0];
    }

    private static Configuration createSharedConfiguration() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        return configuration;
    }

    private XNode parseXml(String sql) {
        String xml = "<script>" + sql + "</script>";
        XPathParser parser = new XPathParser(xml);
        List<XNode> xNodes = parser.evalNodes("script");
        if (xNodes == null || xNodes.size() <= 0) {
            throw new RuntimeException("Can not find sql statement from text: " + sql);
        }
        if (xNodes.size() > 1) {
            throw new RuntimeException("Only support one sql statement for parse");
        }

        return xNodes.get(0);
    }

    public Map<String, Boolean> getParameterNames() {
        Map<String, Boolean> inputParams = new LinkedHashMap<>();
        XmlScriptBuilder builder = new XmlScriptBuilder(configuration, root);
        builder.parseScriptNode(inputParams);

        Map<String, Boolean> names = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : inputParams.entrySet()) {
            String name = entry.getKey();
            String subName = null;
            if (null == entry.getValue()) {
                continue;
            }
            Matcher matcher = pattern.matcher(name);
            while (matcher.find()) {
                name = name.replaceAll(Pattern.quote(matcher.group()), "");
                matcher = pattern.matcher(name);
            }

            int commaIdx = name.indexOf(",");
            if (commaIdx > 0) {
                name = name.substring(0, commaIdx);
            }

            int idx = name.indexOf(".");
            if (idx > 0) {
                subName = name;
                name = name.substring(0, idx);
                if (entry.getValue()) {
                    names.put(name, false);
                    if (null != subName) {
                        names.put(subName, true);
                    }
                } else {
                    names.putIfAbsent(name, entry.getValue());
                    if (null != subName) {
                        names.putIfAbsent(subName, entry.getValue());
                    }
                }
                continue;
            } else {
                if (entry.getValue()) {
                    names.put(name, true);
                    if (null != subName) {
                        names.put(subName, true);
                    }
                } else {
                    names.putIfAbsent(name, entry.getValue());
                    if (null != subName) {
                        names.putIfAbsent(subName, entry.getValue());
                    }
                }
            }
        }

        return names;
    }

    public SqlMeta process(Map<String, Object> params) {
        return process(params, true);
    }

    /**
     * @param dollarSubstitutionAllowed when false, ${} literal substitution is forbidden (S3: open=true public APIs or
     *        the global switch disabled); a template containing ${} throws {@link DollarSubstitutionException};
     *        #{} parameterized placeholders are unaffected
     */
    public SqlMeta process(Map<String, Object> params, boolean dollarSubstitutionAllowed) {
        if (!dollarSubstitutionAllowed && containsDollarToken(xmlSql)) {
            throw new DollarSubstitutionException(
                    "Dollar substitution ${} is not allowed for this API, use #{} instead");
        }
        XmlScriptBuilder builder = new XmlScriptBuilder(configuration, root);
        Map<String, Boolean> inputParams = new HashMap<>();
        SqlSource sqlSource = builder.parseScriptNode(inputParams);
        BoundSql boundSql = sqlSource.getBoundSql(params);

        List<Object> paramValues = new ArrayList<>();
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
            String name = parameterMapping.getProperty();
            Object value = boundSql.getAdditionalParameter(name);
            if (null == value) {
                int idx = name.indexOf(".");
                if (idx > 0) {
                    String objName = name.substring(0, idx);
                    String subName = name.substring(idx + 1);
                    value = params.get(objName);
                    if (value instanceof Map) {
                        value = ((Map) value).get(subName);
                    }
                } else {
                    value = params.get(name);
                }
            }
            paramValues.add(value);
        }

        return new SqlMeta(boundSql.getSql(), paramValues);
    }
}