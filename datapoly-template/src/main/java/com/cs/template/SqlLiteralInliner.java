// Use of this source code is governed by a BSD-style license
package com.cs.template;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Rewrites the bind placeholders MyBatis left in a rendered statement into SQL literals, for
 * delivery paths that hand the statement to an engine accepting no bind parameters at all
 * (MaxCompute's {@code UNLOAD}, for instance). A value never reaches the statement as
 * unqualified text: strings are quoted and escaped, so the statement carries the same data a
 * {@code PreparedStatement} would have been given.
 *
 * <p>Placement is positional, exactly like binding: the n-th {@code ?} outside quoted text and
 * comments receives the n-th value. Nothing else about the rendering changes, so dynamic tags,
 * {@code <foreach>} lists and object sub-parameters keep working — they are the very
 * placeholders and values the ordinary path would have bound. A placeholder count that does not
 * match the value count is refused instead of guessed at.</p>
 */
final class SqlLiteralInliner {

    /** One line of guidance for statements this path cannot express as literals. */
    private static final String SCALAR_HINT = "expand arrays with <foreach> and reference object"
            + " fields individually, or keep the definition on the row pipeline";

    private SqlLiteralInliner() {
    }

    /**
     * @param sql    statement rendered by MyBatis, placeholders already in {@code ?} form
     * @param values bind values in placeholder order
     * @param names  parameter names in the same order, used for diagnostics only (may be null)
     * @return the statement with every placeholder replaced by its literal
     * @throws ParameterInliningException when a value is not scalar or the counts disagree
     */
    static String inline(String sql, List<Object> values, List<String> names) {
        if (null == sql || null == values || values.isEmpty()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length() + 32);
        int placed = 0;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = copyQuoted(sql, i, c, out);
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i = copyLineComment(sql, i, out);
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                i = copyBlockComment(sql, i, out);
                continue;
            }
            if (c != '?') {
                out.append(c);
                i++;
                continue;
            }
            if (placed >= values.size()) {
                throw new ParameterInliningException("the statement has more '?' placeholders than the "
                        + values.size() + " bind value(s) rendered with it; a literal '?' in the SQL cannot be"
                        + " inlined — remove it or author the definition with ${} substitution");
            }
            out.append(literal(values.get(placed), name(names, placed)));
            placed++;
            i++;
        }
        if (placed != values.size()) {
            throw new ParameterInliningException("the statement has " + placed + " '?' placeholder(s) but "
                    + values.size() + " bind value(s); inline delivery needs them to match — check for an"
                    + " unbalanced quote or comment in the SQL");
        }
        return out.toString();
    }

    /**
     * Renders one bind value as a SQL literal. The escape set is the one these engines share
     * for string literals (backslash escapes: {@code \\} and {@code \'}); anything that is not
     * a scalar is reported instead of being rendered as its {@code toString()}.
     */
    static String literal(Object value, String name) {
        if (null == value) {
            return "NULL";
        }
        if (value instanceof String) {
            return quoted((String) value);
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof BigInteger || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new ParameterInliningException("parameter [" + name + "] is " + number
                        + ", which no engine accepts as a numeric literal");
            }
            return BigDecimal.valueOf(number).toPlainString();
        }
        throw new ParameterInliningException("parameter [" + name + "] holds a "
                + value.getClass().getSimpleName() + ", which is not a scalar and cannot be inlined as one"
                + " literal — " + SCALAR_HINT);
    }

    private static String quoted(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ('\\' == c) {
                out.append("\\\\");
            } else if ('\'' == c) {
                out.append("\\'");
            } else if ('\0' == c) {
                throw new ParameterInliningException("a parameter value carries a NUL character,"
                        + " which cannot be represented in a text statement");
            } else {
                out.append(c);
            }
        }
        return out.append('\'').toString();
    }

    private static String name(List<String> names, int index) {
        if (null == names || index >= names.size() || null == names.get(index)) {
            return "#" + (index + 1);
        }
        return names.get(index);
    }

    /** Copies a quoted region (string literal or quoted identifier) and returns the next index. */
    private static int copyQuoted(String sql, int start, char quote, StringBuilder out) {
        int i = start;
        out.append(sql.charAt(i++));
        // backslash escapes belong to string literals; a backtick identifier only knows doubling
        boolean backslashEscapes = '`' != quote;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (backslashEscapes && '\\' == c && i + 1 < sql.length()) {
                out.append(c).append(sql.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    out.append(c).append(c);
                    i += 2;
                    continue;
                }
                out.append(c);
                return i + 1;
            }
            out.append(c);
            i++;
        }
        return i;
    }

    private static int copyLineComment(String sql, int start, StringBuilder out) {
        int i = start;
        while (i < sql.length() && '\n' != sql.charAt(i)) {
            out.append(sql.charAt(i++));
        }
        return i;
    }

    private static int copyBlockComment(String sql, int start, StringBuilder out) {
        int i = start;
        while (i < sql.length()) {
            if ('*' == sql.charAt(i) && i + 1 < sql.length() && '/' == sql.charAt(i + 1)) {
                out.append("*/");
                return i + 2;
            }
            out.append(sql.charAt(i++));
        }
        return i;
    }
}