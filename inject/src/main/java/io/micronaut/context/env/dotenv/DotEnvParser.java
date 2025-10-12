/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env.dotenv;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * Parser for .env file format.
 * Handles quotes, escape sequences, comments, and variable references.
 */
final class DotEnvParser {

    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final char NO_QUOTE = '\0';
    private Logger log;

    DotEnvParser() {}

    DotEnvParser(Logger log) {
        this.log = log;
    }

    /**
     * Parses an input stream containing .env format data.
     *
     * @param input the input stream to parse
     * @return parse context containing resolved and unresolved variables
     * @throws IOException if an I/O error occurs
     */
    DotEnvParseContext parse(InputStream input) throws IOException {
        DotEnvParseContext context = new DotEnvParseContext();
        char quoteChar = NO_QUOTE;
        boolean isMultiLined = false;
        String key = null;
        StringBuilder val = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            int lineNumber = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                try {
                    int delimIndex;

                    if (!isMultiLined) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        delimIndex = line.indexOf('=');
                        if (delimIndex <= 0) {
                            continue;
                        }

                        String originalKey = line.substring(0, delimIndex).trim();
                        key = processKey(originalKey);
                        if (key.isEmpty()) {
                            if (log != null) {
                                log.warn("Skipping invalid key '{}' at line {}", originalKey, lineNumber);
                            }
                            continue;
                        }
                    } else {
                        val.append('\n');
                        delimIndex = -1;
                    }

                    quoteChar = processValue(line, delimIndex + 1, val, quoteChar, key, context);
                    isMultiLined = quoteChar != NO_QUOTE;

                    if (!isMultiLined && !val.isEmpty()) {
                        String value = val.toString();
                        if (context.hasUnresolvedVariable(key)) {
                            context.setUnresolvedValue(key, value);
                        } else {
                            context.addResolvedVariable(key, value);
                        }
                        val.setLength(0);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        }

        return context;
    }

    private String processKey(String key) {
        if (!key.isEmpty() && !VALID_KEY_PATTERN.matcher(key).matches()) { return ""; }
        return key.toLowerCase().replace('_', '.');
    }

    /**
     * Processes a value starting from the given position.
     *
     * @param line the current line being parsed
     * @param begin starting position in the line
     * @param result buffer to append processed characters
     * @param quoteChar current quote context
     * @param key the key for this value
     * @param context parse context for tracking variables
     * @return the quote character state after processing
     */
    private char processValue(String line, int begin, StringBuilder result, char quoteChar,
                              String key, DotEnvParseContext context) {
        int lastChar = -1;

        for (int i = begin; i < line.length(); i++) {
            char c = line.charAt(i);
            switch (c) {
                case ' ':
                    if (!result.isEmpty() || quoteChar != NO_QUOTE) { result.append(c); }
                    break;
                case '"':
                    if (quoteChar == NO_QUOTE) { quoteChar = '"'; }
                    else if (quoteChar == '"') { quoteChar = NO_QUOTE; }
                    else { result.append(c); }
                    lastChar = result.length();
                    break;
                case '\'':
                    if (quoteChar == NO_QUOTE) { quoteChar = '\''; }
                    else if (quoteChar == '\'') { quoteChar = NO_QUOTE; }
                    else { result.append(c); }
                    lastChar = result.length();
                    break;
                case '\\':
                    i = processEscape(line, i, result, quoteChar);
                    lastChar = result.length();
                    break;
                case '$':
                    i = processVariable(line, i, result, quoteChar, key, context);
                    if (i > line.length()) { return NO_QUOTE; }
                    lastChar = result.length();
                    break;
                case '#':
                    if (quoteChar == NO_QUOTE) {
                        if (lastChar > 0 && lastChar < result.length()) {
                            result.delete(lastChar, result.length());
                        }
                        return quoteChar;
                    }
                    result.append(c);
                    lastChar = result.length();
                    break;
                default:
                    result.append(c);
                    lastChar = result.length();
                    break;
            }
        }

        if (quoteChar == NO_QUOTE && lastChar > 0 && lastChar < result.length()) {
            result.delete(lastChar, result.length());
        }

        return quoteChar;
    }

    /**
     * Processes a variable reference ($VAR or ${VAR}).
     *
     * @param line the current line
     * @param index position of the $ character
     * @param result buffer to append normalized variable reference
     * @param quoteChar current quote context
     * @param key the key being processed
     * @param context parse context to track variable dependencies
     * @return new index position after processing
     */
    private int processVariable(String line, int index, StringBuilder result, char quoteChar,
                                String key, DotEnvParseContext context) {
        boolean braceUsed = false;
        StringBuilder varName = new StringBuilder();

        if (quoteChar == '\'') {
            result.append('$');
            return index;
        }

        int next = index + 1;
        if (next < line.length() && line.charAt(next) == '{') {
            braceUsed = true;
            index = next;
        }

        for (int i = index + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (braceUsed && c == '}') {
                if (!varName.isEmpty()) {
                    context.addVariableDependency(key, varName.toString());
                    result.append("${").append(varName).append("}");
                }
                return i;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                if (!braceUsed && !varName.isEmpty()) {
                    context.addVariableDependency(key, varName.toString());
                    result.append("${").append(varName).append("}");
                    return i - 1;
                } else {
                    throw new IllegalArgumentException(
                        "Malformed variable reference starting with '" + varName +
                            "' - variables must contain only letters, digits, and underscores"
                    );
                }
            }
            varName.append(c);
        }

        if (braceUsed) {
            throw new IllegalArgumentException(
                "Unclosed variable brace: ${" + varName + " - missing closing '}'"
            );
        }

        if (!varName.isEmpty()) {
            context.addVariableDependency(key, varName.toString());
            result.append("${").append(varName).append("}");
        }

        return line.length() - 1;
    }

    private int processEscape(String line, int index, StringBuilder result, char quoteChar) {
        char c = line.charAt(index);

        if (quoteChar == '\'') {
            result.append(c);
            return index;
        }

        int next = index + 1;
        if (next < line.length()) {
            char escaped = line.charAt(next);
            return switch (escaped) {
                case 'n', 't', 'r' -> {
                    if (quoteChar == '"') {
                        result.append(getEscapeChar(escaped));
                    } else {
                        result.append(escaped);
                    }
                    yield index + 1;
                }
                case '$' -> {
                    result.append("\\$");
                    yield index + 1;
                }
                default -> {
                    result.append(escaped);
                    yield index + 1;
                }
            };
        }

        return index;
    }

    private char getEscapeChar(char escaped) {
        return switch (escaped) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            default -> escaped;
        };
    }
}
