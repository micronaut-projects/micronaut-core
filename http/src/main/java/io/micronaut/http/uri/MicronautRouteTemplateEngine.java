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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.spi.RouteTemplateEngine;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The engine of the Micronaut URI template language, {@link RouteTemplate#MICRONAUT}. It delegates
 * to {@link UriMatchTemplate} and {@link UriTemplateMatcher}, so routes match exactly as before
 * there were engines. It is always registered, see
 * {@link io.micronaut.http.uri.spi.RouteTemplateEngines}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class MicronautRouteTemplateEngine implements RouteTemplateEngine {

    /**
     * The engine.
     */
    public static final MicronautRouteTemplateEngine INSTANCE = new MicronautRouteTemplateEngine();

    /**
     * The version of the semantics of the engine.
     */
    public static final String VERSION = "1.0";

    private static final Pattern SIMPLE_VARIABLE = Pattern.compile("\\{\\w[\\w-]*}");

    private MicronautRouteTemplateEngine() {
    }

    @Override
    public String id() {
        return RouteTemplate.MICRONAUT;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public ParsedRouteTemplate parse(RouteTemplate template) {
        if (!template.isMicronaut()) {
            throw new IllegalArgumentException("Not a Micronaut route template: " + template);
        }
        return new Parsed(template, null);
    }

    /**
     * A parsed template of an existing {@link UriMatchTemplate}. Its expression is the
     * {@link UriMatchTemplate#toString() string} of the template.
     *
     * @param uriMatchTemplate The template
     * @return The parsed template
     */
    public static ParsedRouteTemplate of(UriMatchTemplate uriMatchTemplate) {
        return new Parsed(null, uriMatchTemplate);
    }

    /**
     * @param template A parsed template
     * @return The {@link UriMatchTemplate} of a template this engine parsed, or {@code null} for a
     * template of another engine
     */
    public static @Nullable UriMatchTemplate uriMatchTemplate(ParsedRouteTemplate template) {
        return template instanceof Parsed parsed ? parsed.uriMatchTemplate() : null;
    }

    /**
     * @param pattern A pattern
     * @return The {@link UriTemplateMatcher} of a pattern this engine created, or {@code null} for a
     * pattern of another engine
     */
    public static @Nullable UriTemplateMatcher uriTemplateMatcher(RoutePattern pattern) {
        return pattern instanceof Pattern0 micronaut ? micronaut.matcher : null;
    }

    @Override
    public ParsedRouteTemplate nest(ParsedRouteTemplate parent, ParsedRouteTemplate child) {
        return of(parsed(parent).uriMatchTemplate().nest(parsed(child).template().expression()));
    }

    @Override
    public ParsedRouteTemplate mount(String prefix, ParsedRouteTemplate template) {
        // the same composition the context path always had
        String mounted = UriTemplate.of(prefix).nest(parsed(template).template().expression()).toString();
        return new Parsed(RouteTemplate.micronaut(mounted), null);
    }

    @Override
    public RoutePattern matcher(ParsedRouteTemplate template) {
        Parsed parsed = parsed(template);
        return new Pattern0(parsed, parsed.matcher());
    }

    private static Parsed parsed(ParsedRouteTemplate template) {
        if (template instanceof Parsed parsed) {
            return parsed;
        }
        throw new IllegalArgumentException("Not a template of the Micronaut route template engine: " + template.template());
    }

    /**
     * The segments the router compares: literal segments, whole-segment simple variables, and
     * anything else as a part that may match any number of segments, e.g. {@code {+path}},
     * {@code {/id}} or a regular expression.
     */
    private static List<RouteTemplateSegment> segments(String template) {
        int query = template.indexOf("{?");
        if (query >= 0) {
            template = template.substring(0, query);
        }
        List<RouteTemplateSegment> result = new ArrayList<>();
        for (String segment : template.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.indexOf('{') < 0) {
                result.add(RouteTemplateSegment.literal(segment));
            } else if (SIMPLE_VARIABLE.matcher(segment).matches()) {
                result.add(RouteTemplateSegment.VARIABLE);
            } else {
                result.add(RouteTemplateSegment.ANY);
            }
        }
        return List.copyOf(result);
    }

    /**
     * A parsed Micronaut template: the {@link UriMatchTemplate} and the matcher are created when
     * first needed, so that parsing a template for its index facts or segments stays cheap.
     */
    private static final class Parsed implements ParsedRouteTemplate {
        private @Nullable RouteTemplate template;
        private @Nullable UriMatchTemplate uriMatchTemplate;
        private @Nullable UriTemplateMatcher matcher;
        private @Nullable List<RouteTemplateVariable> variables;
        private @Nullable List<RouteTemplateSegment> segments;
        private int rawLength = -1;
        private int pathVariableCount = -1;
        private int patternVariableCount = -1;

        /**
         * @param template         The template, or {@code null} for the string of the {@link UriMatchTemplate}
         * @param uriMatchTemplate The {@link UriMatchTemplate}, or {@code null} to parse the template when needed
         */
        Parsed(@Nullable RouteTemplate template, @Nullable UriMatchTemplate uriMatchTemplate) {
            if (template == null && uriMatchTemplate == null) {
                throw new IllegalArgumentException("A template or a URI match template is required");
            }
            this.template = template;
            this.uriMatchTemplate = uriMatchTemplate;
        }

        UriMatchTemplate uriMatchTemplate() {
            UriMatchTemplate t = uriMatchTemplate;
            if (t == null) {
                t = new UriMatchTemplate(template().expression());
                uriMatchTemplate = t;
            }
            return t;
        }

        UriTemplateMatcher matcher() {
            UriTemplateMatcher m = matcher;
            if (m == null) {
                m = new UriTemplateMatcher(uriMatchTemplate().getTemplateString());
                matcher = m;
            }
            return m;
        }

        @Override
        public RouteTemplate template() {
            RouteTemplate t = template;
            if (t == null) {
                t = RouteTemplate.micronaut(uriMatchTemplate().toString());
                template = t;
            }
            return t;
        }

        @Override
        public String engineVersion() {
            return VERSION;
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            List<RouteTemplateVariable> v = variables;
            if (v == null) {
                List<RouteTemplateVariable> result = new ArrayList<>();
                for (UriMatchVariable variable : uriMatchTemplate().getVariables()) {
                    result.add(new RouteTemplateVariable(
                        variable.getName(),
                        variable.isOptional(),
                        variable.isQuery() ? RouteTemplateVariable.Location.QUERY : RouteTemplateVariable.Location.PATH,
                        variable.isExploded()
                    ));
                }
                v = List.copyOf(result);
                variables = v;
            }
            return v;
        }

        @Override
        public String requiredPrefix() {
            return matcher().getRequiredPrefix();
        }

        @Override
        public int rawLength() {
            int r = rawLength;
            if (r < 0) {
                r = matcher().getRawLength();
                rawLength = r;
            }
            return r;
        }

        @Override
        public int pathVariableCount() {
            int c = pathVariableCount;
            if (c < 0) {
                c = matcher().getPathVariableCount();
                pathVariableCount = c;
            }
            return c;
        }

        @Override
        public int patternVariableCount() {
            int c = patternVariableCount;
            if (c < 0) {
                c = matcher().getPatternVariableCount();
                patternVariableCount = c;
            }
            return c;
        }

        @Override
        public List<RouteTemplateSegment> pathSegments() {
            List<RouteTemplateSegment> s = segments;
            if (s == null) {
                s = segments(template().expression());
                segments = s;
            }
            return s;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Parsed other && template().equals(other.template());
        }

        @Override
        public int hashCode() {
            return template().hashCode();
        }

        @Override
        public String toString() {
            return template().expression();
        }
    }

    /**
     * The pattern of a Micronaut template: its {@link UriTemplateMatcher}.
     */
    private static final class Pattern0 implements RoutePattern {
        private final Parsed template;
        private final UriTemplateMatcher matcher;

        Pattern0(Parsed template, UriTemplateMatcher matcher) {
            this.template = template;
            this.matcher = Objects.requireNonNull(matcher);
        }

        @Override
        public ParsedRouteTemplate template() {
            return template;
        }

        @Override
        public @Nullable RouteCaptures match(String path) {
            UriMatchInfo info = matcher.tryMatch(path);
            if (info == null) {
                return null;
            }
            List<RouteTemplateVariable> variables = template.variables();
            List<@Nullable String> values = new ArrayList<>(variables.size());
            for (RouteTemplateVariable variable : variables) {
                Object value = info.getVariableValues().get(variable.name());
                values.add(value == null ? null : value.toString());
            }
            return new RouteCaptures(info.getUri(), variables, values);
        }

        @Override
        public String toString() {
            return matcher.toString();
        }
    }
}
