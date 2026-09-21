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
package io.micronaut.python.processing.diagnostic;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.python.processing.model.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Renders {@link PythonDiagnostic}s the way a compiler prints them: the message, then the
 * location and an excerpt of the source with the span underlined.
 *
 * <pre>
 * [python:unknown-method] Java type [java.util.ArrayList] has no method named [ad]; did you mean [add]?
 *   --&gt; src/main/python/shop/service.py:14:16
 *      |
 *   14 |         values.ad(item.title)
 *      |                ^^
 * </pre>
 *
 * @since 5.3.0
 */
@Experimental
public final class PythonDiagnostics {

    /**
     * The marker introducing the location line of a rendered diagnostic. A message carrying it is
     * already located and needs no further location from the caller.
     */
    public static final String LOCATION_MARKER = "  --> ";

    private static final Pattern NEWLINE = Pattern.compile("\r\n|\r|\n");
    private static final Pattern EXCERPT_LINE = Pattern.compile("^\\s*(\\d+ )?\\|");
    private static final Pattern LOCATION_LINE = Pattern.compile("(?m)^\\s*--> \\S");

    private PythonDiagnostics() {
    }

    /**
     * Renders a diagnostic without an excerpt.
     *
     * @param diagnostic The diagnostic
     * @return The message with its rule and location
     */
    public static String render(PythonDiagnostic diagnostic) {
        return render(diagnostic, path -> null);
    }

    /**
     * Renders a diagnostic with an excerpt of the source.
     *
     * @param diagnostic The diagnostic
     * @param sources    Resolves the text of a source from its path; {@code null} when unavailable
     * @return The message with its rule, location and excerpt
     */
    public static String render(PythonDiagnostic diagnostic, Function<String, @Nullable CharSequence> sources) {
        StringBuilder out = new StringBuilder("[python:").append(diagnostic.rule()).append("] ").append(diagnostic.message());
        SourceSpan span = diagnostic.span();
        if (span == null) {
            return out.toString();
        }
        String ls = System.lineSeparator();
        out.append(ls).append(LOCATION_MARKER).append(span.location());
        CharSequence source = sources.apply(span.path());
        if (source == null) {
            return out.toString();
        }
        List<String> lines = List.of(NEWLINE.split(source, -1));
        if (span.line() < 1 || span.line() > lines.size()) {
            return out.toString();
        }
        String line = lines.get(span.line() - 1);
        String number = Integer.toString(span.line());
        String gutter = " ".repeat(number.length() + 1) + "|";
        int start = Math.max(0, Math.min(span.column() - 1, line.length()));
        // a span ending on a later line is underlined to the end of its first line
        int end = span.endLine() > span.line() ? line.length() : Math.min(Math.max(span.endColumn() - 1, start + 1), Math.max(line.length(), start + 1));
        out.append(ls).append(gutter)
            .append(ls).append(' ').append(number).append(" | ").append(line)
            .append(ls).append(gutter).append(' ').append(caretPrefix(line, start)).append("^".repeat(end - start));
        return out.toString();
    }

    /**
     * The blank prefix that puts the caret under the span: the tabs of the source line are kept, so
     * the caret lines up however wide a tab is displayed, and every other character becomes a space.
     */
    private static String caretPrefix(String line, int start) {
        StringBuilder prefix = new StringBuilder(start);
        for (int i = 0; i < start; i++) {
            prefix.append(i < line.length() && line.charAt(i) == '\t' ? '\t' : ' ');
        }
        return prefix.toString();
    }

    /**
     * @param message A message
     * @return Whether the message is a rendered diagnostic that already carries its location
     */
    public static boolean isLocated(@Nullable String message) {
        return message != null && LOCATION_LINE.matcher(message).find();
    }

    /**
     * @param line A line of a message
     * @return Whether the line belongs to the excerpt of a rendered diagnostic, whose indentation is significant
     */
    public static boolean isExcerptLine(String line) {
        return EXCERPT_LINE.matcher(line).find();
    }
}
