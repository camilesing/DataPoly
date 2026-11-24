// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.*;
import org.apache.ibatis.scripting.xmltags.TextSqlNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.regex.*;

/**
 * MyBatis dynamic SQL over author-written text: the template is parsed as
 * {@code <script> … </script>}, which is what gives it dynamic tags and {@code #{}} / {@code ${}}
 * placeholders. Comparisons and {@code &} are written as they are in SQL — see {@link SqlXmlTexts}
 * — and templates escaped the old way ({@code &lt;=}, {@code <![CDATA[ ]]>}) keep working unchanged.
 */
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
     * Detects whether the template contains a ${} literal substitution token (S3) where the renderer will
     * look for it — in the text MyBatis reads back, i.e. after XML decoding and the backslash-unescaping of
     * {@code XNode.getStringBody()}. The author's raw text is another string: {@code \$\{} or {@code &#36;{}
     * reads as {@code ${} only there. #{} and plain text are unaffected.
     */
    public static boolean containsDollarToken(String sqlText) {
        if (null == sqlText || sqlText.isEmpty()) {
            return false;
        }
        XNode root;
        try {
            root = parseXml(sqlText);
        } catch (RuntimeException unparsable) {
            // the template is rejected elsewhere; for a verdict on it the raw text is all there is
            return containsRawDollarToken(sqlText);
        }
        return containsDollarToken(root);
    }

    private static boolean containsDollarToken(XNode node) {
        NodeList children = node.getNode().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            XNode child = node.newXNode(children.item(i));
            short type = child.getNode().getNodeType();
            if (Node.TEXT_NODE == type || Node.CDATA_SECTION_NODE == type) {
                // the same detector XmlScriptBuilder uses to hand a text node to ${} substitution
                if (new TextSqlNode(child.getStringBody()).isDynamic()) {
                    return true;
                }
            } else if (containsDollarToken(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRawDollarToken(String sqlText) {
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

    private static XNode parseXml(String sql) {
        // The template is not XML: bare '<' / '&' (comparisons, string literals) are escaped for the
        // parser here while dynamic tags keep their meaning — see SqlXmlTexts for the rules
        String xml = "<script>" + SqlXmlTexts.toXmlSafe(sql) + "</script>";
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
        Rendered rendered = render(params, dollarSubstitutionAllowed);
        return new SqlMeta(rendered.sql, rendered.values);
    }

    /**
     * Renders the template and then turns every {@code #{} } the renderer left behind into an SQL
     * literal, for delivery paths whose engine accepts no bind parameters (server-side exports such
     * as MaxCompute's {@code UNLOAD}). Template semantics are untouched: {@code ${} } substitution,
     * including the ban that {@code dollarSubstitutionAllowed == false} enforces, resolves exactly
     * as in {@link #process(Map, boolean)} — only the placeholder style of {@code #{} } changes.
     *
     * @throws ParameterInliningException when a value is not a scalar or the placeholder count does
     *        not match the value count
     */
    public SqlMeta processInlined(Map<String, Object> params, boolean dollarSubstitutionAllowed) {
        Rendered rendered = render(params, dollarSubstitutionAllowed);
        if (rendered.values.isEmpty()) {
            return new SqlMeta(rendered.sql, rendered.values);
        }
        return new SqlMeta(SqlLiteralInliner.inline(rendered.sql, rendered.values, rendered.names),
                Collections.emptyList());
    }

    private Rendered render(Map<String, Object> params, boolean dollarSubstitutionAllowed) {
        if (!dollarSubstitutionAllowed && containsDollarToken(root)) {
            throw new DollarSubstitutionException(
                    "Dollar substitution ${} is not allowed for this API, use #{} instead");
        }
        XmlScriptBuilder builder = new XmlScriptBuilder(configuration, root);
        Map<String, Boolean> inputParams = new HashMap<>();
        SqlSource sqlSource = builder.parseScriptNode(inputParams);
        BoundSql boundSql = sqlSource.getBoundSql(params);

        List<String> paramNames = new ArrayList<>();
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
            paramNames.add(name);
            paramValues.add(value);
        }

        return new Rendered(boundSql.getSql(), paramNames, paramValues);
    }

    /** One rendering: the statement, and the bind names/values in placeholder order. */
    private static final class Rendered {

        private final String sql;
        private final List<String> names;
        private final List<Object> values;

        private Rendered(String sql, List<String> names, List<Object> values) {
            this.sql = sql;
            this.names = names;
            this.values = values;
        }
    }
}