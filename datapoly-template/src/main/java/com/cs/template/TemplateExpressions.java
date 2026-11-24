// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.ognl.ASTConst;
import org.apache.ibatis.ognl.ASTCtor;
import org.apache.ibatis.ognl.ASTEval;
import org.apache.ibatis.ognl.ASTMethod;
import org.apache.ibatis.ognl.ASTProperty;
import org.apache.ibatis.ognl.ASTStaticField;
import org.apache.ibatis.ognl.ASTStaticMethod;
import org.apache.ibatis.ognl.Node;
import org.apache.ibatis.ognl.Ognl;
import org.apache.ibatis.ognl.OgnlException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * What a template expression may say. {@code test}, {@code bind value}, {@code foreach collection} and a
 * {@code ${}} token are handed to OGNL as written, and OGNL understands static references, constructors
 * and reflective navigation — enough for an API author to read files and environment variables on the
 * executor host, which is exactly what the script sandbox (S1) exists to prevent.
 *
 * <p>The verdict is taken on OGNL's own syntax tree, i.e. by the very parser MyBatis evaluates with:
 * property paths, literals, operators and methods on the request's own values are untouched, and no
 * quoting or spelling of a forbidden construct can hide it.
 */
final class TemplateExpressions {

    /** Methods that hand out classes, class loaders or reflective handles. */
    private static final Set<String> REFLECTION_METHODS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("getClass", "getClassLoader", "forName", "newInstance", "loadClass",
                    "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods",
                    "getField", "getFields", "getDeclaredField", "getDeclaredFields",
                    "getConstructor", "getConstructors", "getDeclaredConstructor",
                    "getResource", "getResourceAsStream")));

    /** Navigation paths to the same: {@code x.class} and {@code x['class']} both lead to a Class. */
    private static final Set<String> REFLECTION_PROPERTIES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("class", "classLoader")));

    private TemplateExpressions() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @param attribute    where the expression sits, for the message ({@code test}, {@code bind value}, …)
     * @param expression   the expression exactly as written
     * @throws BuilderException when the expression is not parseable or reaches for a forbidden construct
     */
    static void check(String attribute, String expression) {
        if (null == expression || expression.trim().isEmpty()) {
            return;
        }
        Node root;
        try {
            root = (Node) Ognl.parseExpression(expression);
        } catch (OgnlException e) {
            throw new BuilderException("Invalid " + attribute + " expression: " + expression, e);
        }
        String verdict = verdict(root);
        if (null != verdict) {
            throw new BuilderException(
                    "Template " + attribute + " expression may not use " + verdict + ": " + expression);
        }
    }

    private static String verdict(Node node) {
        if (node instanceof ASTStaticMethod || node instanceof ASTStaticField) {
            return "a static reference (@class@member)";
        }
        if (node instanceof ASTCtor) {
            return "a constructor (new)";
        }
        if (node instanceof ASTEval) {
            return "expression evaluation";
        }
        if (node instanceof ASTMethod && REFLECTION_METHODS.contains(((ASTMethod) node).getMethodName())) {
            return "the reflective method " + ((ASTMethod) node).getMethodName() + "()";
        }
        if (node instanceof ASTProperty && leadsToReflection((ASTProperty) node)) {
            return "the reflective property " + node.jjtGetChild(0) + "";
        }
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            String verdict = verdict(node.jjtGetChild(i));
            if (null != verdict) {
                return verdict;
            }
        }
        return null;
    }

    private static boolean leadsToReflection(ASTProperty property) {
        if (property.jjtGetNumChildren() == 0) {
            return false;
        }
        Node name = property.jjtGetChild(0);
        return name instanceof ASTConst && REFLECTION_PROPERTIES.contains(String.valueOf(((ASTConst) name).getValue()));
    }
}
