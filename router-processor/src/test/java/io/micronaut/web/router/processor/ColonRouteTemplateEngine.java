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
package io.micronaut.web.router.processor;

import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RoutePattern;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateSegment;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngine;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A route template language for tests only, registered with the service loader of the tests: a
 * segment {@code :name} is a variable of exactly one segment, and everything else, braces
 * included, is literal. {@code /items/{id:3}} is therefore literal text here, while it is a
 * variable of at most three characters for the Micronaut engine.
 *
 * <p>{@link ColonRouteTemplateCompiler} lowers its templates; the same language under the
 * identifier {@link #RUNTIME_ID} has no compiler, and is matched at runtime only.</p>
 */
public class ColonRouteTemplateEngine implements RouteTemplateEngine {

    public static final String ID = "test.colon";
    public static final String RUNTIME_ID = "test.colon-runtime";

    private final String id;

    public ColonRouteTemplateEngine() {
        this(ID);
    }

    protected ColonRouteTemplateEngine(String id) {
        this.id = id;
    }

    public static RouteTemplate template(String expression) {
        return RouteTemplate.of(ID, expression);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public ParsedRouteTemplate parse(RouteTemplate template) {
        if (!id.equals(template.engineId())) {
            throw new IllegalArgumentException("Not a colon template: " + template);
        }
        String expression = template.expression();
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
                segments.add(new Segment(part.substring(1), true));
            } else {
                segments.add(new Segment(part, false));
            }
        }
        return new Parsed(template, List.copyOf(segments));
    }

    @Override
    public ParsedRouteTemplate nest(ParsedRouteTemplate parent, ParsedRouteTemplate child) {
        Parsed p = (Parsed) parent;
        Parsed c = (Parsed) child;
        List<Segment> segments = new ArrayList<>(p.segments);
        segments.addAll(c.segments);
        String childExpression = c.template.expression();
        String expression = p.template.expression() + (childExpression.startsWith("/") ? childExpression : '/' + childExpression);
        return new Parsed(RouteTemplate.of(id, expression), List.copyOf(segments));
    }

    @Override
    public ParsedRouteTemplate mount(String prefix, ParsedRouteTemplate template) {
        // the prefix is literal: a ':' in it is not a variable
        Parsed parsed = (Parsed) template;
        List<Segment> segments = new ArrayList<>();
        for (String part : prefix.split("/")) {
            if (!part.isEmpty()) {
                segments.add(new Segment(part, false));
            }
        }
        segments.addAll(parsed.segments);
        return new Parsed(RouteTemplate.of(id, prefix + parsed.template.expression()), List.copyOf(segments));
    }

    @Override
    public RoutePattern matcher(ParsedRouteTemplate template) {
        Parsed parsed = (Parsed) template;
        return new RoutePattern() {
            @Override
            public ParsedRouteTemplate template() {
                return parsed;
            }

            @Override
            public @Nullable RouteCaptures match(String path) {
                String normalized = UriTemplateMatcher.normalizeForMatching(path);
                String[] parts = normalized.isEmpty() || "/".equals(normalized) ? new String[0] : normalized.substring(1).split("/", -1);
                if (parts.length != parsed.segments.size()) {
                    return null;
                }
                List<@Nullable String> values = new ArrayList<>();
                for (int i = 0; i < parts.length; i++) {
                    Segment segment = parsed.segments.get(i);
                    if (segment.variable) {
                        if (parts[i].isEmpty()) {
                            return null;
                        }
                        values.add(parts[i]);
                    } else if (!segment.text.equals(parts[i])) {
                        return null;
                    }
                }
                return new RouteCaptures(normalized, parsed.variables(), values);
            }
        };
    }

    /**
     * The same language without a compiler.
     */
    public static final class Runtime extends ColonRouteTemplateEngine {
        public Runtime() {
            super(RUNTIME_ID);
        }
    }

    /**
     * The same language with its own order of specificity, the reverse of the Micronaut one for the
     * literal text: fewer literal characters first.
     */
    public static final class Ordered extends ColonRouteTemplateEngine {
        public static final String ORDERED_ID = "test.colon-ordered";

        public Ordered() {
            super(ORDERED_ID);
        }

        @Override
        public java.util.Optional<java.util.Comparator<ParsedRouteTemplate>> comparator() {
            return java.util.Optional.of(java.util.Comparator.comparingInt(ParsedRouteTemplate::rawLength));
        }
    }

    /**
     * The same language with a route selector: the first accepted type a route produces.
     */
    public static final class Selecting extends ColonRouteTemplateEngine implements io.micronaut.web.router.spi.RouteMatchSelector {
        public static final String SELECTING_ID = "test.colon-selecting";

        public Selecting() {
            super(SELECTING_ID);
        }

        @Override
        public List<Selection> select(io.micronaut.http.HttpRequest<?> request, List<io.micronaut.web.router.UriRouteMatch<?, ?>> matches) {
            List<io.micronaut.http.MediaType> accepted = new ArrayList<>(request.accept());
            if (accepted.isEmpty()) {
                accepted.add(io.micronaut.http.MediaType.ALL_TYPE);
            }
            for (io.micronaut.http.MediaType accept : accepted) {
                for (io.micronaut.web.router.UriRouteMatch<?, ?> match : matches) {
                    for (io.micronaut.http.MediaType produced : match.getRouteInfo().getProduces()) {
                        if (accept.matches(produced)) {
                            return List.of(Selection.of(match, produced));
                        }
                    }
                }
            }
            return List.of();
        }
    }

    record Segment(String text, boolean variable) {
    }

    record Parsed(RouteTemplate template, List<Segment> segments) implements ParsedRouteTemplate {

        @Override
        public String engineVersion() {
            return "1";
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            List<RouteTemplateVariable> variables = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.variable) {
                    variables.add(RouteTemplateVariable.path(segment.text));
                }
            }
            return variables;
        }

        @Override
        public String requiredPrefix() {
            if (segments.isEmpty()) {
                return "";
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
        public int pathVariableCount() {
            return variables().size();
        }

        @Override
        public int patternVariableCount() {
            // the language has no regular expressions
            return 0;
        }

        @Override
        public List<RouteTemplateSegment> pathSegments() {
            List<RouteTemplateSegment> result = new ArrayList<>();
            for (Segment segment : segments) {
                result.add(segment.variable ? RouteTemplateSegment.VARIABLE : RouteTemplateSegment.literal(segment.text));
            }
            return result;
        }
    }
}
