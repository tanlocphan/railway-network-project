package com.railway.setup.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a .sql file into statements the way the mysql command-line client
 * does: it understands DELIMITER lines, quotes, backticks and comments, so
 * stored procedures with ';' inside their body arrive at the server whole.
 */
public final class SqlScript {

    public record Statement(String sql, int line) {

        /** A short, readable label such as "CREATE PROCEDURE sp_book_ticket". */
        public String label() {
            String flat = stripComments(sql).replaceAll("\\s+", " ").trim();
            Matcher m = NAMED.matcher(flat);
            if (m.find()) {
                return (m.group(1) + " " + m.group(2)).replaceAll("\\s+", " ").toUpperCase(Locale.ROOT)
                        + " " + m.group(3).replace("`", "");
            }
            return flat.length() > 90 ? flat.substring(0, 87) + "..." : flat;
        }

        public boolean returnsRows() {
            String head = stripComments(sql).trim().toUpperCase(Locale.ROOT);
            return head.startsWith("SELECT") || head.startsWith("WITH") || head.startsWith("SHOW")
                    || head.startsWith("DESC") || head.startsWith("EXPLAIN") || head.startsWith("CALL")
                    || head.startsWith("(");
        }
    }

    private static final Pattern NAMED = Pattern.compile(
            "^(CREATE|DROP|ALTER|INSERT(?: IGNORE)? INTO|DELETE FROM|UPDATE|TRUNCATE(?: TABLE)?)"
                    + "\\s+(?:DEFINER\\s*=\\s*\\S+\\s+)?((?:TABLE|DATABASE|SCHEMA|PROCEDURE|FUNCTION|VIEW|TRIGGER|INDEX|USER|EVENT)"
                    + "(?:\\s+IF\\s+(?:NOT\\s+)?EXISTS)?|)\\s*(`?[\\w$.']+`?(?:@`?[\\w.']+`?)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTINE = Pattern.compile(
            "CREATE\\s+(?:DEFINER\\s*=\\s*\\S+\\s+)?(?:FUNCTION|PROCEDURE)\\s+`?(\\w+)`?",
            Pattern.CASE_INSENSITIVE);

    private SqlScript() {
    }

    public static List<Statement> parse(String text) {
        List<Statement> result = new ArrayList<>();
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        int startLine = 1;
        int line = 1;
        int i = 0;
        int n = text.length();
        if (n > 0 && text.charAt(0) == '﻿') {
            i = 1;
        }
        boolean atLineStart = true;
        boolean started = false;

        while (i < n) {
            // DELIMITER is a client command; it is only valid at the start of a line
            if (atLineStart && startsWithIgnoreCase(text, skipSpaces(text, i), "DELIMITER")
                    && stripComments(current.toString()).isBlank()) {
                int from = skipSpaces(text, i) + "DELIMITER".length();
                int end = text.indexOf('\n', from);
                if (end < 0) {
                    end = n;
                }
                String value = text.substring(from, end).trim();
                if (!value.isEmpty() && (from >= n || Character.isWhitespace(text.charAt(from)))) {
                    delimiter = value;
                    current.setLength(0);
                    started = false;
                    i = end;
                    continue;
                }
            }

            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                atLineStart = true;
                current.append(c);
                i++;
                continue;
            }
            atLineStart = false;

            // comments
            if (c == '#' || (c == '-' && i + 1 < n && text.charAt(i + 1) == '-'
                    && (i + 2 >= n || Character.isWhitespace(text.charAt(i + 2))))) {
                int end = text.indexOf('\n', i);
                if (end < 0) {
                    end = n;
                }
                current.append(text, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                String block = text.substring(i, end);
                line += count(block, '\n');
                current.append(block);
                i = end;
                continue;
            }
            // the statement starts at its first character that is not a comment
            if (!started && !Character.isWhitespace(c)) {
                started = true;
                startLine = line;
            }
            // quoted text
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < n) {
                    char q = text.charAt(j);
                    if (q == '\\' && c != '`' && j + 1 < n) {
                        j += 2;
                        continue;
                    }
                    if (q == c) {
                        if (j + 1 < n && text.charAt(j + 1) == c) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                int end = Math.min(n, j + 1);
                String quoted = text.substring(i, end);
                line += count(quoted, '\n');
                current.append(quoted);
                i = end;
                continue;
            }
            // end of statement
            if (text.startsWith(delimiter, i)) {
                add(result, current, startLine);
                started = false;
                i += delimiter.length();
                continue;
            }
            current.append(c);
            i++;
        }
        add(result, current, startLine);
        return result;
    }

    /** Names of every function and procedure the script creates. */
    public static Set<String> routineNames(String text) {
        Set<String> names = new LinkedHashSet<>();
        for (Statement s : parse(text)) {
            Matcher m = ROUTINE.matcher(stripComments(s.sql()));
            if (m.find()) {
                names.add(m.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static void add(List<Statement> result, StringBuilder current, int startLine) {
        String sql = current.toString().trim();
        current.setLength(0);
        if (!stripComments(sql).isBlank()) {
            result.add(new Statement(sql, startLine));
        }
    }

    static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*(?!!).*?\\*/", " ")
                .replaceAll("(?m)(^|\\s)--(\\s.*)?$", " ")
                .replaceAll("(?m)^\\s*#.*$", " ");
    }

    private static int skipSpaces(String text, int i) {
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\r')) {
            i++;
        }
        return i;
    }

    private static boolean startsWithIgnoreCase(String text, int at, String word) {
        return text.regionMatches(true, at, word, 0, word.length());
    }

    private static int count(String s, char c) {
        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                k++;
            }
        }
        return k;
    }
}
