package com.helianhealth.iflow.tasks.jdbc;

import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.jayway.jsonpath.DocumentContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 从 payload 读取 SQL 中的确定路径（如 $.id、$.items[0].code），改写为命名绑定参数。
 * IN $.codes 自动补齐括号；IN ($.codes) 沿用模板括号。列表保留原始类型，交给
 * NamedParameterJdbcTemplate 展开；执行 SQL 保持参数绑定，另生成带参数值的 SQL 供运行时展示。
 * SQL 字符串、引用标识符及注释原样保留，不解释其中的任务路径。
 */
final class SqlTemplate {

    static final String BIND_PREFIX = "_p";

    private SqlTemplate() {}

    record Bound(String sql, Map<String, Object> parameters, String renderedSql) {}

    static Bound bind(String sql, JdbcVendor vendor, Map<String, Object> payload) {
        StringBuilder rewritten = new StringBuilder(sql.length());
        StringBuilder rendered = new StringBuilder(sql.length());
        Map<String, Object> bound = new LinkedHashMap<>();
        DocumentContext root = null;
        String previousToken = "";

        for (int i = 0; i < sql.length();) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                rewritten.append(c);
                rendered.append(c);
                i++;
                continue;
            }
            int commentEnd = endOfComment(sql, i, vendor);
            if (commentEnd > i) {
                rewritten.append(sql, i, commentEnd);
                rendered.append(sql, i, commentEnd);
                i = commentEnd;
                continue;
            }
            int literalEnd = endOfLiteral(sql, i, vendor);
            if (literalEnd > i) {
                rewritten.append(sql, i, literalEnd);
                rendered.append(sql, i, literalEnd);
                previousToken = "literal";
                i = literalEnd;
                continue;
            }
            if (c == '$' && i + 1 < sql.length()
                    && (sql.charAt(i + 1) == '.' || sql.charAt(i + 1) == '[')) {
                int end = endOfPath(sql, i);
                String path = sql.substring(i, end);
                if (root == null) {
                    if (payload == null) throw new IllegalArgumentException(
                            "JDBC payload must be an object to resolve " + path);
                    root = ParametersUtils.parse(payload);
                }
                Object value = readValue(root, path);
                String name = BIND_PREFIX + bound.size();
                bound.put(name, value);
                boolean needsParentheses = "IN".equalsIgnoreCase(previousToken);
                if (needsParentheses) rewritten.append('(');
                rewritten.append(':').append(name);
                if (needsParentheses) rewritten.append(')');
                if (needsParentheses) rendered.append('(');
                rendered.append(renderValue(value, vendor));
                if (needsParentheses) rendered.append(')');
                previousToken = "parameter";
                i = end;
                continue;
            }
            // Consume whole identifiers: SQL names such as V$SESSION or foo$.bar
            // cannot introduce a task path. IN remains a significant token.
            int end = i + 1;
            if (isIdentifierPart(c)) {
                while (end < sql.length() && isIdentifierPart(sql.charAt(end))) end++;
            }
            previousToken = sql.substring(i, end);
            rewritten.append(previousToken);
            rendered.append(previousToken);
            i = end;
        }
        return new Bound(rewritten.toString(), bound, rendered.toString());
    }

    private static String renderValue(Object value, JdbcVendor vendor) {
        if (value == null) return "NULL";
        if (value instanceof Collection<?> values) {
            StringJoiner joined = new StringJoiner(", ");
            for (Object element : values) joined.add(renderValue(element, vendor));
            return joined.toString();
        }
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean flag) {
            return isPostgres(vendor) ? (flag ? "TRUE" : "FALSE") : (flag ? "1" : "0");
        }
        String text = value.toString();
        if (vendor == JdbcVendor.MYSQL) text = text.replace("\\", "\\\\");
        return "'" + text.replace("'", "''") + "'";
    }

    private static Object readValue(DocumentContext root, String path) {
        try {
            Object value = root.read(path);
            if (value instanceof Collection<?> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("empty arrays cannot be bound to SQL");
                }
                for (Object element : values) validateScalar(element);
            } else {
                validateScalar(value);
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot resolve JDBC payload path '" + path + "': " + e.getMessage(), e);
        }
    }

    private static void validateScalar(Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>
                || (value != null && value.getClass().isArray())) {
            throw new IllegalArgumentException("SQL parameters must be scalars or flat arrays of scalars");
        }
    }

    /** Read only definite paths; punctuation such as '-', '+', '::' belongs to SQL. */
    private static int endOfPath(String sql, int start) {
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == '.') {
                i++;
                if (i >= sql.length() || !isIdentifierStart(sql.charAt(i))) throw invalidPath(start);
                while (i < sql.length() && isIdentifierPart(sql.charAt(i))) i++;
            } else if (sql.charAt(i) == '[') {
                i++;
                if (i >= sql.length()) throw invalidPath(start);
                char quote = sql.charAt(i);
                if (quote == '\'' || quote == '"') {
                    i++;
                    while (i < sql.length() && sql.charAt(i) != quote) {
                        if (sql.charAt(i) == '\\') i++;
                        i++;
                    }
                    if (i >= sql.length()) throw invalidPath(start);
                    i++;
                } else {
                    int indexStart = i;
                    while (i < sql.length() && sql.charAt(i) >= '0' && sql.charAt(i) <= '9') i++;
                    if (i == indexStart) throw invalidPath(start);
                    try {
                        Integer.parseInt(sql.substring(indexStart, i));
                    } catch (NumberFormatException e) {
                        throw invalidPath(start);
                    }
                }
                if (i >= sql.length() || sql.charAt(i) != ']') throw invalidPath(start);
                i++;
            } else {
                break;
            }
        }
        if (i < sql.length() && (isIdentifierPart(sql.charAt(i)) || sql.charAt(i) == ']' || sql.charAt(i) == '(')) {
            throw invalidPath(start);
        }
        return i;
    }

    private static IllegalArgumentException invalidPath(int start) {
        return new IllegalArgumentException("Invalid JDBC payload path at index " + start
                + ": use definite fields and non-negative array indexes, e.g. $.items[0].code");
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    private static boolean isPostgres(JdbcVendor vendor) {
        return vendor == JdbcVendor.POSTGRES || vendor == JdbcVendor.OPENGAUSS || vendor == JdbcVendor.KINGBASE;
    }

    private static int endOfComment(String sql, int start, JdbcVendor vendor) {
        boolean dashComment = sql.startsWith("--", start)
                && (vendor != JdbcVendor.MYSQL || start + 2 == sql.length()
                    || Character.isWhitespace(sql.charAt(start + 2)));
        if (dashComment || (vendor == JdbcVendor.MYSQL && sql.charAt(start) == '#')) {
            int i = start;
            while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') i++;
            return i;
        }
        if (sql.startsWith("/*", start)) {
            boolean nested = isPostgres(vendor) || vendor == JdbcVendor.SQLSERVER;
            int depth = 1;
            for (int i = start + 2; i < sql.length() - 1; i++) {
                if (nested && sql.startsWith("/*", i)) {
                    depth++;
                    i++;
                } else if (sql.startsWith("*/", i)) {
                    if (--depth == 0) return i + 2;
                    i++;
                }
            }
            throw new IllegalArgumentException("Unterminated SQL comment at index " + start);
        }
        return start;
    }

    private static int endOfLiteral(String sql, int start, JdbcVendor vendor) {
        char quote = sql.charAt(start);
        if (isPostgres(vendor) && quote == '$') return endOfDollarQuoted(sql, start);
        if (vendor == JdbcVendor.ORACLE && (quote == 'q' || quote == 'Q')
                && start + 2 < sql.length() && sql.charAt(start + 1) == '\'') {
            char delimiter = sql.charAt(start + 2);
            char close = switch (delimiter) {
                case '[' -> ']';
                case '(' -> ')';
                case '{' -> '}';
                case '<' -> '>';
                default -> delimiter;
            };
            int end = sql.indexOf("" + close + '\'', start + 3);
            if (end < 0) throw new IllegalArgumentException("Unterminated SQL literal at index " + start);
            return end + 2;
        }
        boolean postgresEscape = isPostgres(vendor) && (quote == 'E' || quote == 'e')
                && start + 1 < sql.length() && sql.charAt(start + 1) == '\'';
        int quoteStart = postgresEscape ? start + 1 : start;
        if (postgresEscape) quote = '\'';
        boolean bracket = vendor == JdbcVendor.SQLSERVER && quote == '[';
        if (quote != '\'' && quote != '"' && quote != 0x60 && !bracket) return start;
        char close = bracket ? ']' : quote;
        for (int i = quoteStart + 1; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if ((vendor == JdbcVendor.MYSQL || postgresEscape) && c == '\\') {
                i++;
            } else if (c == close) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == close) {
                    i++;
                } else {
                    return i + 1;
                }
            }
        }
        throw new IllegalArgumentException("Unterminated SQL literal at index " + start);
    }

    private static int endOfDollarQuoted(String sql, int start) {
        int tagEnd = start + 1;
        if (tagEnd < sql.length() && sql.charAt(tagEnd) != '$') {
            if (!Character.isLetter(sql.charAt(tagEnd)) && sql.charAt(tagEnd) != '_') return start;
            while (tagEnd < sql.length()
                    && (Character.isLetterOrDigit(sql.charAt(tagEnd)) || sql.charAt(tagEnd) == '_')) tagEnd++;
        }
        if (tagEnd >= sql.length() || sql.charAt(tagEnd) != '$') return start;
        String tag = sql.substring(start, tagEnd + 1);
        int close = sql.indexOf(tag, tagEnd + 1);
        if (close < 0) throw new IllegalArgumentException("Unterminated SQL dollar quote at index " + start);
        return close + tag.length();
    }
}
