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
package io.micronaut.web.router;

import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateParser;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A route template language for tests only: a segment {@code :name} is a variable of exactly one
 * segment, and everything else, braces included, is literal. {@code /items/{id:3}} is therefore
 * literal text here, while it is a variable of at most three characters for the URI templates. A
 * segment {@code :name(regex)} is a variable of one segment that matches the regular expression.
 */
public class ColonRouteTemplateEngine implements RouteTemplateParser {

    public static final String ID = "test.colon";

    public static final ColonRouteTemplateEngine INSTANCE = new ColonRouteTemplateEngine();

    private final String id;

    public ColonRouteTemplateEngine() {
        this(ID);
    }

    protected ColonRouteTemplateEngine(String id) {
        this.id = id;
    }

    public static RouteTemplate template(String expression) {
        return INSTANCE.parse(expression);
    }

    /**
     * @param template A template
     * @return The language of the template if it is a route selector, else {@code null}
     */
    public static @Nullable RouteMatchSelector selectorOf(RouteTemplate template) {
        return template instanceof Parsed parsed && parsed.language instanceof RouteMatchSelector selector ? selector : null;
    }

    /**
     * @return The name of the language, for the messages
     */
    public String name() {
        return id;
    }

    /**
     * @param rawPath The raw path of a request
     * @return The path the templates of this language match, by default the raw path
     */
    protected String matchingPath(String rawPath) {
        return rawPath;
    }

    /**
     * @return Whether the language ignores parts of the raw path, so that a required prefix may only
     * be the first literal segment
     */
    protected boolean ignoresPartsOfThePath() {
        return false;
    }

    @Override
    public RouteTemplate parse(String expression) {
        if (!expression.isEmpty() && expression.charAt(0) != '/') {
            throw new IllegalArgumentException("A colon template starts with a slash: " + expression);
        }
        List<Segment> segments = new ArrayList<>();
        for (String part : expression.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.charAt(0) == ':') {
                if (part.length() == 1) {
                    throw new IllegalArgumentException("A variable without a name: " + expression);
                }
                int open = part.indexOf('(');
                if (open > 1 && part.endsWith(")")) {
                    // :name(regex): a variable of one segment constrained by a regular expression
                    segments.add(new Segment(part.substring(1, open), true, Pattern.compile(part.substring(open + 1, part.length() - 1))));
                } else if (part.endsWith("?")) {
                    // :name?: an optional variable of the last segment, absent from the captures
                    // of a path without it, as a template of another language may leave it out
                    segments.add(new Segment(part.substring(1, part.length() - 1), true, null, true));
                } else {
                    segments.add(new Segment(part.substring(1), true, null));
                }
            } else {
                segments.add(new Segment(part, false, null));
            }
        }
        return new Parsed(this, expression, List.copyOf(segments));
    }

    @Override
    public String toString() {
        return id;
    }

    private record Segment(String text, boolean variable, @Nullable Pattern pattern, boolean optional) {
        Segment(String text, boolean variable, @Nullable Pattern pattern) {
            this(text, variable, pattern, false);
        }
    }

    private record Parsed(ColonRouteTemplateEngine language, String expression, List<Segment> segments) implements RouteTemplate {

        @Override
        public @Nullable RouteCaptures match(String rawPath) {
            String normalized = UriTemplateMatcher.normalizeForMatching(language.matchingPath(rawPath));
            String[] parts = normalized.isEmpty() || "/".equals(normalized) ? new String[0] : normalized.substring(1).split("/", -1);
            boolean lastOptional = !segments.isEmpty() && segments.get(segments.size() - 1).optional;
            boolean absent = lastOptional && parts.length == segments.size() - 1;
            if (parts.length != segments.size() && !absent) {
                return null;
            }
            List<@Nullable String> values = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                Segment segment = segments.get(i);
                if (segment.variable) {
                    if (parts[i].isEmpty() || segment.pattern != null && !segment.pattern.matcher(parts[i]).matches()) {
                        return null;
                    }
                    values.add(parts[i]);
                } else if (!segment.text.equals(parts[i])) {
                    return null;
                }
            }
            if (absent) {
                values.add(null);
            }
            return RouteCaptures.copyOf(normalized, variables(), values);
        }

        @Override
        public RouteTemplate mount(String prefix) {
            // the prefix is literal: a ':' in it is not a variable
            List<Segment> all = new ArrayList<>();
            for (String part : prefix.split("/")) {
                if (!part.isEmpty()) {
                    all.add(new Segment(part, false, null));
                }
            }
            all.addAll(segments);
            return new Parsed(language, prefix + expression, List.copyOf(all));
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            List<RouteTemplateVariable> variables = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.variable) {
                    variables.add(RouteTemplateVariable.of(segment.text, segment.optional, RouteTemplateVariable.Location.PATH, false, segment.pattern != null));
                }
            }
            return List.copyOf(variables);
        }

        @Override
        public String requiredPrefix() {
            if (segments.isEmpty()) {
                return "";
            }
            if (language.ignoresPartsOfThePath()) {
                // the raw path may have more in its first segment, e.g. matrix parameters
                Segment first = segments.get(0);
                return first.variable ? "/" : "/" + first.text;
            }
            StringBuilder prefix = new StringBuilder();
            for (Segment segment : segments) {
                prefix.append('/');
                if (segment.variable) {
                    return prefix.toString();
                }
                prefix.append(segment.text);
            }
            return prefix.toString();
        }

        @Override
        public int rawLength() {
            int length = 0;
            for (Segment segment : segments) {
                length += 1 + (segment.variable ? 0 : segment.text.length());
            }
            return length;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Parsed other && language == other.language && expression.equals(other.expression);
        }

        @Override
        public int hashCode() {
            return expression.hashCode();
        }

        @Override
        public String toString() {
            return language + ":" + expression;
        }
    }
}
