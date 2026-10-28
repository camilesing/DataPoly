// Use of this source code is governed by a BSD-style license
package com.cs.template;

import org.apache.ibatis.builder.BuilderException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Makes author-written SQL safe for the MyBatis XML parser without asking the author to escape
 * anything.
 *
 * A template is parsed as {@code <script> … </script>}, so XML rules used to leak into SQL
 * authoring: a bare {@code <} (as in {@code amount <= 100}, {@code a<b}) or a bare {@code &}
 * (as in {@code like 'A&B%'}) made the parser fail with "The content of elements must consist of
 * well-formed character data or markup", and authors had to write {@code &lt;=} or wrap fragments
 * in CDATA. Here only the dynamic tags this module implements count as markup
 * ({@link XmlScriptBuilder#DYNAMIC_TAGS}); everything else XML would reject is escaped, and that
 * escaping is invisible end to end — the XML parser decodes it back to the character the author
 * typed, so the statement handed to the driver keeps the author's own formatting. {@code >} needs
 * no help (XML allows it in character data), which is why {@code >=} always worked.
 *
 * Existing escaped templates keep working unchanged: a well-formed entity reference and a CDATA or
 * comment section are copied verbatim, exactly as the XML parser expects to find them.
 */
final class SqlXmlTexts {

    private static final String CDATA_OPEN = "<![CDATA[";
    private static final String CDATA_CLOSE = "]]>";
    private static final String CDATA_TAIL = "]]";
    private static final String COMMENT_OPEN = "<!--";
    private static final String COMMENT_CLOSE = "-->";
    private static final Set<String> XML_ENTITIES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("amp", "lt", "gt", "quot", "apos")));
    private static final String MARKUP_HINT =
            "Supported dynamic tags: " + String.join(", ", XmlScriptBuilder.DYNAMIC_TAGS)
                    + "; comparisons are written '<' or '<=', with no slash.";

    private SqlXmlTexts() {
    }

    /**
     * Escapes what XML would reject in {@code sql}, leaving markup (dynamic tags, CDATA, comments)
     * and existing entity references untouched.
     *
     * @throws BuilderException when a closing tag names an element that is not a supported dynamic
     *         tag — {@code </} has no meaning in SQL, so that is a misspelled tag name
     */
    static String toXmlSafe(String sql) {
        if (null == sql || sql.isEmpty()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length() + 16);
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '<') {
                i = appendMarkupOrEscaped(sql, i, out);
            } else if (c == '&') {
                i = appendAmp(sql, i, out);
            } else if (c == '>' && endsWithPlainText(out, CDATA_TAIL)) {
                // the only place XML forbids a bare '>' in character data
                out.append("&gt;");
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Handles the '<' at {@code start}; returns the index to continue scanning from. */
    private static int appendMarkupOrEscaped(String sql, int start, StringBuilder out) {
        if (sql.startsWith(COMMENT_OPEN, start)) {
            return copyBlock(sql, start, COMMENT_OPEN.length(), COMMENT_CLOSE, out);
        }
        if (sql.startsWith(CDATA_OPEN, start)) {
            return copyBlock(sql, start, CDATA_OPEN.length(), CDATA_CLOSE, out);
        }
        boolean closing = sql.startsWith("</", start);
        int nameStart = start + (closing ? 2 : 1);
        int nameEnd = nameEnd(sql, nameStart);
        String name = nameEnd > nameStart ? sql.substring(nameStart, nameEnd) : "";
        if (XmlScriptBuilder.DYNAMIC_TAGS.contains(name) && isTagDelimiter(sql, nameEnd)) {
            return copyTag(sql, start, nameEnd, out);
        }
        if (isDynamicTagIgnoringCase(name) && isTagDelimiter(sql, nameEnd)) {
            throw new BuilderException("Unknown element " + (closing ? "</" : "<") + name
                    + "> in SQL statement: dynamic tag names are case sensitive, write "
                    + (closing ? "</" : "<") + name.toLowerCase(Locale.ROOT) + ">. " + MARKUP_HINT);
        }
        if (closing && !name.isEmpty()) {
            throw new BuilderException("Unknown element </" + name + "> in SQL statement. " + MARKUP_HINT);
        }
        out.append("&lt;");
        return start + 1;
    }

    /**
     * Copies {@code <name …>} as markup, escaping inside the tag what XML rejects there — attribute
     * values are their own context, so {@code <if test='amount <= 100'>} is written the same way a
     * SQL author writes it. Quotes are tracked so a {@code >} inside an attribute does not end the
     * tag. An unterminated tag is copied as far as it goes and left for the parser to report, with
     * the element name in its message.
     */
    private static int copyTag(String sql, int start, int nameEnd, StringBuilder out) {
        out.append(sql, start, nameEnd);
        int i = nameEnd;
        char quote = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                    out.append(c);
                } else if (c == '<') {
                    out.append("&lt;");
                } else if (c == '&') {
                    i = appendAmp(sql, i, out);
                    continue;
                } else {
                    out.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
            } else if (c == '>') {
                out.append(c);
                return i + 1;
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '&') {
                i = appendAmp(sql, i, out);
                continue;
            } else {
                out.append(c);
            }
            i++;
        }
        return i;
    }

    /** Appends the entity reference at {@code i} verbatim, or escapes a bare '&'; returns the next index. */
    private static int appendAmp(String sql, int i, StringBuilder out) {
        int end = entityEnd(sql, i);
        if (end > 0) {
            out.append(sql, i, end);
            return end;
        }
        out.append("&amp;");
        return i + 1;
    }

    /** Copies a CDATA or comment section verbatim, closing delimiter included. */
    private static int copyBlock(String sql, int start, int openLength, String close, StringBuilder out) {
        int end = sql.indexOf(close, start + openLength);
        if (end < 0) {
            out.append(sql, start, sql.length());
            return sql.length();
        }
        int after = end + close.length();
        out.append(sql, start, after);
        return after;
    }

    /** End index (exclusive) of a well-formed entity reference at {@code start} ('&'), or -1. */
    private static int entityEnd(String sql, int start) {
        int i = start + 1;
        if (i >= sql.length()) {
            return -1;
        }
        if (sql.charAt(i) == '#') {
            i++;
            boolean hex = i < sql.length() && (sql.charAt(i) == 'x' || sql.charAt(i) == 'X');
            if (hex) {
                i++;
            }
            int digits = i;
            while (i < sql.length() && (hex ? isHex(sql.charAt(i)) : Character.isDigit(sql.charAt(i)))) {
                i++;
            }
            return i > digits && i < sql.length() && sql.charAt(i) == ';' ? i + 1 : -1;
        }
        int nameStart = i;
        while (i < sql.length() && Character.isLetterOrDigit(sql.charAt(i))) {
            i++;
        }
        boolean named = i < sql.length() && sql.charAt(i) == ';'
                && XML_ENTITIES.contains(sql.substring(nameStart, i));
        return named ? i + 1 : -1;
    }

    private static int nameEnd(String sql, int start) {
        int i = start;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            boolean partOfName = i == start ? Character.isLetter(c)
                    : Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
            if (!partOfName) {
                break;
            }
            i++;
        }
        return i;
    }

    /** A tag name must end where the name does: {@code <if_id} and {@code a<if(1)} are not tags. */
    private static boolean isTagDelimiter(String sql, int index) {
        if (index >= sql.length()) {
            return true;
        }
        char c = sql.charAt(index);
        return c == '>' || c == '/' || Character.isWhitespace(c);
    }

    /**
     * Whether {@code name} is a dynamic tag spelled with the wrong case ({@code IF}). XML does not
     * fold case, so that is a spelling mistake and not a comparison operand — reporting it here
     * beats turning the tag into literal text and failing somewhere in the database.
     */
    private static boolean isDynamicTagIgnoringCase(String name) {
        for (String tag : XmlScriptBuilder.DYNAMIC_TAGS) {
            if (tag.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithPlainText(StringBuilder out, String suffix) {
        int offset = out.length() - suffix.length();
        if (offset < 0) {
            return false;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (out.charAt(offset + i) != suffix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}