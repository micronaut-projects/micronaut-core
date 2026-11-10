/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.compile;

/**
 * What symbol processors can read of a Kotlin source that its class files do not hold: the KDoc comments, which a
 * processor reads through KSP's {@code docString}, the annotations, a {@code SOURCE} retention one included, which
 * kotlinc writes nowhere, and the package and imports, which tell which annotation a name is. Two versions of a source whose classes differ in method bodies alone and whose texts here are
 * the same give the processors the same input.
 *
 * <p>The text is the package and import directives and every KDoc comment and every annotation with its arguments, in
 * the order they appear, wherever they
 * appear, bodies included: a source is read token by token, skipping strings, characters and other comments, not
 * parsed. A {@code @} right after a name is a label, not an annotation. Whatever this reads more than it needs only
 * makes an edit go through the processors.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class KotlinDeclarationText {

    private final String source;
    private final StringBuilder text = new StringBuilder();
    private int position;

    private KotlinDeclarationText(String source) {
        this.source = source;
    }

    /**
     * @param source A Kotlin source
     * @return Its KDoc comments and annotations, each on a line of its own
     */
    static String of(String source) {
        KotlinDeclarationText reader = new KotlinDeclarationText(source);
        reader.code(false);
        return reader.text.toString();
    }

    /**
     * Reads code to the end, or to the brace that closes a string template's expression.
     */
    private void code(boolean template) {
        int braces = 0;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (c == '/' && next(1) == '/') {
                skipLine();
            } else if (c == '/' && next(1) == '*') {
                comment();
            } else if (c == '"') {
                string();
            } else if (c == '\'') {
                character();
            } else if (c == '@' && !(position > 0 && isNamePart(source.charAt(position - 1)))) {
                annotation();
            } else if (!template && atLineStart() && (source.startsWith("import", position) || source.startsWith("package", position))
                && !isNamePart(next(source.startsWith("import", position) ? 6 : 7))) {
                directive();
            } else if (template && c == '{') {
                braces++;
                position++;
            } else if (template && c == '}') {
                position++;
                if (braces-- == 0) {
                    return;
                }
            } else {
                position++;
            }
        }
    }

    /**
     * Whether only blanks are before this position on its line.
     */
    private boolean atLineStart() {
        for (int i = position - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '\n' || c == '\r') {
                return true;
            } else if (c != ' ' && c != '\t' && c != '\uFEFF') {
                return false;
            }
        }
        return true;
    }

    /**
     * A package or import directive, to the end of its line, with what follows it there: further directives after a
     * semicolon among it.
     */
    private void directive() {
        int start = position;
        while (position < source.length() && source.charAt(position) != '\n' && source.charAt(position) != '\r') {
            if (source.startsWith("//", position) || source.startsWith("/*", position)) {
                break;
            }
            position++;
        }
        text.append(source, start, position).append('\n');
    }

    private void skipLine() {
        while (position < source.length() && source.charAt(position) != '\n') {
            position++;
        }
    }

    /**
     * A block comment, which nests in Kotlin; a KDoc one is kept.
     */
    private void comment() {
        int start = position;
        boolean kdoc = next(2) == '*' && next(3) != '/';
        int depth = 0;
        while (position < source.length()) {
            if (source.startsWith("/*", position)) {
                depth++;
                position += 2;
            } else if (source.startsWith("*/", position)) {
                depth--;
                position += 2;
                if (depth == 0) {
                    break;
                }
            } else {
                position++;
            }
        }
        if (kdoc) {
            text.append(source, start, position).append('\n');
        }
    }

    /**
     * A string, raw or not, with the expressions of its templates read as code.
     */
    private void string() {
        boolean raw = source.startsWith("\"\"\"", position);
        position += raw ? 3 : 1;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (raw && source.startsWith("\"\"\"", position)) {
                position += 3;
                // a raw string may end with further quotes, which belong to it
                while (position < source.length() && source.charAt(position) == '"') {
                    position++;
                }
                return;
            } else if (!raw && c == '"') {
                position++;
                return;
            } else if (!raw && c == '\\') {
                position += 2;
            } else if (c == '$' && next(1) == '{') {
                position += 2;
                code(true);
            } else if (!raw && c == '\n') {
                // an unterminated string ends with its line
                return;
            } else {
                position++;
            }
        }
    }

    private void character() {
        position++;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (c == '\\') {
                position += 2;
            } else if (c == '\'' || c == '\n') {
                position++;
                return;
            } else {
                position++;
            }
        }
    }

    /**
     * An annotation: {@code @}, a use-site target, a name or a bracketed list, and arguments in parentheses.
     */
    private void annotation() {
        int start = position;
        position++;
        name();
        if (position < source.length() && source.charAt(position) == ':') {
            // a use-site target, @field:Inject
            position++;
            name();
        }
        if (position < source.length() && source.charAt(position) == '[') {
            balanced('[', ']');
        } else if (position < source.length() && source.charAt(position) == '<') {
            balanced('<', '>');
        }
        // the arguments may follow a blank
        int end = position;
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
        if (position < source.length() && source.charAt(position) == '(') {
            balanced('(', ')');
        } else {
            position = end;
        }
        text.append(source, start, position).append('\n');
    }

    /**
     * A name, qualified or not, in backticks or not.
     */
    private void name() {
        while (position < source.length()) {
            char c = source.charAt(position);
            if (c == '`') {
                int end = source.indexOf('`', position + 1);
                position = end < 0 ? source.length() : end + 1;
            } else if (isNamePart(c) || c == '.' && position + 1 < source.length() && isNamePart(source.charAt(position + 1))) {
                position++;
            } else {
                return;
            }
        }
    }

    /**
     * From an opening bracket to the one that closes it, reading the strings, characters and comments inside as such.
     */
    private void balanced(char open, char close) {
        int depth = 0;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (c == '"') {
                string();
            } else if (c == '\'') {
                character();
            } else if (c == '/' && next(1) == '/') {
                skipLine();
            } else if (c == '/' && next(1) == '*') {
                int before = text.length();
                comment();
                // what is inside an annotation is in its own text
                text.setLength(before);
            } else {
                position++;
                if (c == open) {
                    depth++;
                } else if (c == close && --depth == 0) {
                    return;
                }
            }
        }
    }

    private char next(int offset) {
        int index = position + offset;
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '`';
    }
}
