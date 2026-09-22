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

import io.micronaut.http.uri.spi.RouteTemplateEngine;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Micronaut route template engine is a view of {@link UriMatchTemplate} and
 * {@link UriTemplateMatcher}: its facts and matches are theirs.
 */
class RouteTemplateEngineTest {

    private static final String[] TEMPLATES = {
        "/", "", "/books", "/books/", "/books/{id}", "/books/{id}/authors", "/books/{id:\\d+}", "/books/{id:3}",
        "/books{/id}", "/books{.ext}", "/books{?q}", "/books{?q,r}", "/books/{+path}", "/files/{+path}", "/files",
        "/{name}", "/{name}/details", "/api/v1/users/{id}", "/api/v2/{+rest}", "/a/{b}/c", "/a/b{c}", "/x-{id}",
        "/foo{bar}", "/café/{x}", "/books/{path*}"
    };

    private static final String[] PATHS = {
        "/", "", "/books", "/books/", "/books/12", "/books/abc", "/books/abcd", "/books/12/authors", "/books.json",
        "/books?q=1", "/books/12?x=/y", "/books/a/b/c", "/files", "/files/a/b", "/foo", "/foo/details",
        "/api/v1/users/7", "/api/v2/x/y", "/a/b/c", "/a/x/c", "/a/bz", "/x-1", "/foobar", "/café/1", "//", "/books//"
    };

    @Test
    void theMicronautEngineDescribesTheTemplateLikeTheMatcher() {
        for (String template : TEMPLATES) {
            ParsedRouteTemplate parsed = MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.micronaut(template));
            UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(template).getTemplateString());
            assertEquals(RouteTemplate.MICRONAUT, parsed.engineId(), template);
            assertEquals(MicronautRouteTemplateEngine.VERSION, parsed.engineVersion(), template);
            assertEquals(matcher.getRequiredPrefix(), parsed.requiredPrefix(), template);
            assertEquals(matcher.getRawLength(), parsed.rawLength(), template);
            assertEquals(matcher.getPathVariableCount(), parsed.pathVariableCount(), template);
            List<UriMatchVariable> variables = new UriMatchTemplate(template).getVariables();
            assertEquals(variables.size(), parsed.variables().size(), template);
            for (int i = 0; i < variables.size(); i++) {
                UriMatchVariable expected = variables.get(i);
                RouteTemplateVariable actual = parsed.variables().get(i);
                assertEquals(expected.getName(), actual.name(), template);
                assertEquals(expected.isOptional(), actual.optional(), template);
                assertEquals(expected.isQuery(), actual.location() == RouteTemplateVariable.Location.QUERY, template);
                assertEquals(expected.isExploded(), actual.exploded(), template);
            }
        }
    }

    @Test
    void theMicronautEngineMatchesLikeTheMatcher() {
        for (String template : TEMPLATES) {
            ParsedRouteTemplate parsed = MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.micronaut(template));
            RoutePattern pattern = MicronautRouteTemplateEngine.INSTANCE.matcher(parsed);
            UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(template).getTemplateString());
            for (String path : PATHS) {
                UriMatchInfo expected = matcher.tryMatch(path);
                RouteCaptures actual = pattern.match(path);
                if (expected == null) {
                    assertNull(actual, template + " " + path);
                    continue;
                }
                assertEquals(expected.getUri(), actual.path(), template + " " + path);
                for (Map.Entry<String, Object> entry : expected.getVariableValues().entrySet()) {
                    assertEquals(entry.getValue() == null ? null : entry.getValue().toString(), actual.value(entry.getKey()), template + " " + path);
                }
                // the compatibility projection has the same values and variables
                UriMatchInfo projected = actual.toUriMatchInfo();
                Map<String, Object> expectedValues = new java.util.LinkedHashMap<>(expected.getVariableValues());
                expectedValues.values().removeIf(java.util.Objects::isNull);
                assertEquals(expectedValues, projected.getVariableValues(), template + " " + path);
                assertEquals(expected.getVariableMap().keySet(), projected.getVariableMap().keySet(), template + " " + path);
                for (UriMatchVariable variable : expected.getVariables()) {
                    UriMatchVariable projectedVariable = projected.getVariableMap().get(variable.getName());
                    assertEquals(variable.isOptional(), projectedVariable.isOptional(), template);
                    assertEquals(variable.isQuery(), projectedVariable.isQuery(), template);
                    assertEquals(variable.isExploded(), projectedVariable.isExploded(), template);
                }
            }
        }
    }

    @Test
    void theMicronautEngineComposesLikeUriMatchTemplate() {
        MicronautRouteTemplateEngine engine = MicronautRouteTemplateEngine.INSTANCE;
        ParsedRouteTemplate nested = engine.nest(engine.parse(RouteTemplate.micronaut("/books")), engine.parse(RouteTemplate.micronaut("/{id}")));
        assertEquals(new UriMatchTemplate("/books").nest("/{id}"), MicronautRouteTemplateEngine.uriMatchTemplate(nested));
        ParsedRouteTemplate mounted = engine.mount("/ctx", engine.parse(RouteTemplate.micronaut("/books/{id}")));
        assertEquals(UriTemplate.of("/ctx").nest("/books/{id}").toString(), mounted.template().expression());
    }

    @Test
    void theMicronautSegmentsDescribeTheOverlapOfTemplates() {
        assertEquals(List.of(RouteTemplateSegment.literal("books"), RouteTemplateSegment.VARIABLE),
            MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.micronaut("/books/{id}{?q}")).pathSegments());
        assertEquals(List.of(RouteTemplateSegment.literal("books"), RouteTemplateSegment.ANY),
            MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.micronaut("/books/{+path}")).pathSegments());
        assertEquals(RouteTemplateSegment.ANY,
            MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.micronaut("/books{/id}")).pathSegments().getFirst());
    }

    @Test
    void templatesOfDifferentEnginesAreDifferent() {
        assertNotEquals(RouteTemplate.micronaut("/items/{id:3}"), RouteTemplate.of("other", "/items/{id:3}"));
        assertEquals(RouteTemplate.of(RouteTemplate.MICRONAUT, "/items"), RouteTemplate.micronaut("/items"));
        assertTrue(RouteTemplate.micronaut("/x").isMicronaut());
        assertFalse(RouteTemplate.of("other", "/x").isMicronaut());
        assertThrows(IllegalArgumentException.class, () -> RouteTemplate.of(" ", "/x"));
        assertThrows(IllegalArgumentException.class, () -> MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.of("other", "/x")));
    }

    @Test
    void theRegistryAlwaysHasTheMicronautEngine() {
        assertSame(MicronautRouteTemplateEngine.INSTANCE, RouteTemplateEngines.defaults().engine(RouteTemplate.MICRONAUT));
        assertSame(MicronautRouteTemplateEngine.INSTANCE, RouteTemplateEngines.of(List.of()).engine(RouteTemplate.MICRONAUT));
    }

    @Test
    void duplicateEngineIdentifiersAreRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            RouteTemplateEngines.of(List.of(new StubEngine("test.stub"), new StubEngine("test.stub"))));
        assertTrue(e.getMessage().contains("test.stub"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> RouteTemplateEngines.of(List.of(new StubEngine(RouteTemplate.MICRONAUT))));
    }

    @Test
    void aMissingEngineIsAnErrorNotMicronaut() {
        RouteTemplateEngines engines = RouteTemplateEngines.of(List.of(new StubEngine("test.stub")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> engines.parse(RouteTemplate.of("test.missing", "/x")));
        assertTrue(e.getMessage().contains("test.missing"), e.getMessage());
        assertTrue(e.getMessage().contains("[micronaut, test.stub]"), e.getMessage());
    }

    @Test
    void anEngineMustDescribeTheFactsOfThePolicy() {
        StubEngine engine = new StubEngine("test.stub");
        RouteTemplateEngines engines = RouteTemplateEngines.of(List.of(engine));
        assertEquals("/a/", engines.parse(RouteTemplate.of("test.stub", "/a/")).requiredPrefix());

        engine.prefix = "a";
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> engines.parse(RouteTemplate.of("test.stub", "/a/")));
        assertTrue(e.getMessage().contains("does not start with a slash"), e.getMessage());
        engine.prefix = "/a/";
        engine.rawLength = -1;
        assertThrows(IllegalStateException.class, () -> engines.parse(RouteTemplate.of("test.stub", "/a/")));
    }

    @Test
    void nestingAcrossEnginesIsRejected() {
        RouteTemplateEngines engines = RouteTemplateEngines.of(List.of(new StubEngine("test.stub")));
        ParsedRouteTemplate micronaut = engines.parse(RouteTemplate.micronaut("/a"));
        ParsedRouteTemplate stub = engines.parse(RouteTemplate.of("test.stub", "/b"));
        assertThrows(IllegalArgumentException.class, () -> engines.nest(micronaut, stub));
        assertThrows(IllegalArgumentException.class, () -> engines.nest(stub, micronaut));
    }

    @Test
    void capturesProjectToUriMatchInfo() {
        List<RouteTemplateVariable> variables = List.of(
            RouteTemplateVariable.path("a"),
            new RouteTemplateVariable("b", true, RouteTemplateVariable.Location.PATH, false),
            new RouteTemplateVariable("q", true, RouteTemplateVariable.Location.QUERY, true),
            RouteTemplateVariable.path("a"));
        RouteCaptures captures = new RouteCaptures("/x/y", variables, Arrays.asList("1", null, "3", "4"));
        UriMatchInfo info = captures.toUriMatchInfo();
        // the first occurrence of a name, absent values left out
        assertEquals(Map.of("a", "1", "q", "3"), info.getVariableValues());
        assertTrue(info.getVariableMap().get("b").isOptional());
        assertTrue(info.getVariableMap().get("q").isQuery());
        assertTrue(info.getVariableMap().get("q").isExploded());
        assertFalse(info.getVariableMap().get("a").isOptional());
        assertEquals("1", captures.value("a"));
        assertThrows(IllegalArgumentException.class, () -> new RouteCaptures("/", variables, List.of("1")));
    }

    /**
     * An engine whose facts a test sets.
     */
    private static final class StubEngine implements RouteTemplateEngine {
        private final String id;
        String prefix = "/a/";
        int rawLength = 3;

        StubEngine(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String version() {
            return "0";
        }

        @Override
        public ParsedRouteTemplate parse(RouteTemplate template) {
            StubEngine engine = this;
            return new ParsedRouteTemplate() {
                @Override
                public RouteTemplate template() {
                    return template;
                }

                @Override
                public String engineVersion() {
                    return "0";
                }

                @Override
                public List<RouteTemplateVariable> variables() {
                    return List.of();
                }

                @Override
                public String requiredPrefix() {
                    return engine.prefix;
                }

                @Override
                public int rawLength() {
                    return engine.rawLength;
                }

                @Override
                public int pathVariableCount() {
                    return 0;
                }

                @Override
                public @Nullable List<RouteTemplateSegment> pathSegments() {
                    return null;
                }
            };
        }

        @Override
        public ParsedRouteTemplate nest(ParsedRouteTemplate parent, ParsedRouteTemplate child) {
            return parse(RouteTemplate.of(id, parent.template().expression() + child.template().expression()));
        }

        @Override
        public ParsedRouteTemplate mount(String prefix, ParsedRouteTemplate template) {
            return parse(RouteTemplate.of(id, prefix + template.template().expression()));
        }

        @Override
        public RoutePattern matcher(ParsedRouteTemplate template) {
            return new RoutePattern() {
                @Override
                public ParsedRouteTemplate template() {
                    return template;
                }

                @Override
                public @Nullable RouteCaptures match(String path) {
                    return path.equals(template.template().expression()) ? new RouteCaptures(path, List.of(), new ArrayList<>()) : null;
                }
            };
        }
    }
}
