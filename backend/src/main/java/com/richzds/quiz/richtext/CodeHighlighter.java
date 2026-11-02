package com.richzds.quiz.richtext;

import java.util.Locale;
import java.util.Set;

/**
 * 很小的单遍扫描高亮器：认注释、字符串、数字、关键字四类记号，输出带内联样式的 span。
 * App 端 rich-text 组件不认 class，所以颜色全部写在 style 里。所有文本都先转义。
 */
public final class CodeHighlighter {

    static final String KEYWORD = "color:#0033b3;font-weight:bold;";
    static final String STRING = "color:#067d17;";
    static final String COMMENT = "color:#8c8c8c;font-style:italic;";
    static final String NUMBER = "color:#1750eb;";
    static final String DIRECTIVE = "color:#9e880d;";

    private static final Set<String> JAVA = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "var", "record", "yield", "true", "false", "null");
    private static final Set<String> C = Set.of(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
            "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return",
            "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
            "volatile", "while", "bool", "true", "false", "NULL", "class", "public", "private", "protected",
            "new", "delete", "namespace", "using", "template", "this", "nullptr");
    private static final Set<String> PYTHON = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
            "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with",
            "yield", "self", "print");
    /** SQL 关键字不区分大小写，这里统一存大写。 */
    private static final Set<String> SQL = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "FULL", "CROSS", "ON", "AS", "GROUP",
            "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "UNION", "ALL", "IN", "IS", "NULL",
            "LIKE", "BETWEEN", "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END", "COUNT", "SUM", "AVG", "MIN",
            "MAX", "BEGIN", "START", "COMMIT", "ROLLBACK", "TRANSACTION", "DEFAULT", "UNIQUE", "CHECK",
            "CONSTRAINT", "INT", "INTEGER", "BIGINT", "VARCHAR", "CHAR", "TEXT", "DATE", "DATETIME",
            "DECIMAL", "ASC", "DESC", "GRANT", "REVOKE", "TRIGGER", "PROCEDURE", "AUTO_INCREMENT", "WITH",
            "ANY", "SOME", "FOR", "LOCK", "SHARE", "MODE", "ISOLATION", "LEVEL", "READ", "COMMITTED",
            "UNCOMMITTED", "REPEATABLE", "SERIALIZABLE", "EXPLAIN", "TRUE", "FALSE");

    private enum Lang { JAVA, C, PYTHON, SQL }

    private CodeHighlighter() {
    }

    /** 高亮一段代码；不认识的语言只做转义。 */
    public static String highlight(String code, String language) {
        Lang lang = resolve(language);
        if (lang == null) {
            return escape(code);
        }
        StringBuilder out = new StringBuilder(code.length() * 2);
        int i = 0;
        int n = code.length();
        boolean lineStart = true;
        while (i < n) {
            char ch = code.charAt(i);
            int end;
            if ((end = commentEnd(code, i, lang)) > i) {
                span(out, COMMENT, code.substring(i, end));
            } else if (lang == Lang.C && lineStart && ch == '#') {
                end = lineEnd(code, i);
                span(out, DIRECTIVE, code.substring(i, end));
            } else if ((end = stringEnd(code, i, lang)) > i) {
                span(out, STRING, code.substring(i, end));
            } else if (Character.isDigit(ch)) {
                end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '.'
                        || code.charAt(end) == '_')) {
                    end++;
                }
                span(out, NUMBER, code.substring(i, end));
            } else if (Character.isJavaIdentifierStart(ch)) {
                end = i;
                while (end < n && Character.isJavaIdentifierPart(code.charAt(end))) {
                    end++;
                }
                String word = code.substring(i, end);
                if (isKeyword(word, lang)) {
                    span(out, KEYWORD, word);
                } else {
                    out.append(escape(word));
                }
            } else {
                end = i + 1;
                out.append(escape(String.valueOf(ch)));
            }
            for (int k = i; k < end; k++) {
                char c = code.charAt(k);
                if (c == '\n') {
                    lineStart = true;
                } else if (!Character.isWhitespace(c)) {
                    lineStart = false;
                }
            }
            i = end;
        }
        return out.toString();
    }

    private static Lang resolve(String language) {
        if (language == null) {
            return null;
        }
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "java" -> Lang.JAVA;
            case "c", "cpp", "c++", "h" -> Lang.C;
            case "python", "py", "python3" -> Lang.PYTHON;
            case "sql", "mysql" -> Lang.SQL;
            default -> null;
        };
    }

    private static boolean isKeyword(String word, Lang lang) {
        return switch (lang) {
            case JAVA -> JAVA.contains(word);
            case C -> C.contains(word);
            case PYTHON -> PYTHON.contains(word);
            case SQL -> SQL.contains(word.toUpperCase(Locale.ROOT));
        };
    }

    /** 从 i 开始若是注释，返回注释结束位置，否则返回 i。 */
    private static int commentEnd(String s, int i, Lang lang) {
        boolean slashStyle = lang == Lang.JAVA || lang == Lang.C;
        if ((slashStyle && s.startsWith("//", i))
                || (lang == Lang.PYTHON && s.charAt(i) == '#')
                || (lang == Lang.SQL && (s.startsWith("--", i) || s.charAt(i) == '#'))) {
            return lineEnd(s, i);
        }
        if ((slashStyle || lang == Lang.SQL) && s.startsWith("/*", i)) {
            int close = s.indexOf("*/", i + 2);
            return close < 0 ? s.length() : close + 2;
        }
        return i;
    }

    /** 从 i 开始若是字符串字面量，返回结束位置（含引号），否则返回 i。未闭合的到行尾为止。 */
    private static int stringEnd(String s, int i, Lang lang) {
        char q = s.charAt(i);
        if (q != '"' && q != '\'' && !(lang == Lang.SQL && q == '`')) {
            return i;
        }
        if (lang == Lang.PYTHON && (s.startsWith("\"\"\"", i) || s.startsWith("'''", i))) {
            int close = s.indexOf(s.substring(i, i + 3), i + 3);
            return close < 0 ? s.length() : close + 3;
        }
        int j = i + 1;
        while (j < s.length()) {
            char c = s.charAt(j);
            if (c == '\\' && lang != Lang.SQL && j + 1 < s.length()) {
                j += 2;
                continue;
            }
            if (c == q) {
                // SQL 里两个单引号表示一个字面单引号
                if (lang == Lang.SQL && j + 1 < s.length() && s.charAt(j + 1) == q) {
                    j += 2;
                    continue;
                }
                return j + 1;
            }
            if (c == '\n') {
                return j;
            }
            j++;
        }
        return j;
    }

    private static int lineEnd(String s, int i) {
        int nl = s.indexOf('\n', i);
        return nl < 0 ? s.length() : nl;
    }

    private static void span(StringBuilder out, String style, String text) {
        out.append("<span style=\"").append(style).append("\">").append(escape(text)).append("</span>");
    }

    public static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
