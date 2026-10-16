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

import io.micronaut.http.uri.UriRouteTemplate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Micronaut route template is a view of {@link UriMatchTemplate} and
 * {@link UriTemplateMatcher}: its facts and matches are theirs.
 */
class RouteTemplateTest {

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
    void aMicronautTemplateDescribesTheTemplateLikeTheMatcher() {
        for (String template : TEMPLATES) {
            RouteTemplate parsed = UriRouteTemplate.of(template);
            UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(template).getTemplateString());
            assertEquals(template, parsed.expression(), template);
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
    void aMicronautTemplateMatchesLikeTheMatcher() {
        for (String template : TEMPLATES) {
            RouteTemplate pattern = UriRouteTemplate.of(template);
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
    void aUriTemplateMountsLikeTheContextPath() {
        RouteTemplate mounted = UriRouteTemplate.of("/books/{id}").mount("/ctx");
        assertEquals(UriTemplate.of("/ctx").nest("/books/{id}").toString(), mounted.expression());
        // a URI match template keeps its template
        UriMatchTemplate uriMatchTemplate = new UriMatchTemplate("/books/{id}");
        assertSame(uriMatchTemplate, UriRouteTemplate.of(uriMatchTemplate).getUriMatchTemplate());
        assertEquals(UriRouteTemplate.of("/books/{id}"), UriRouteTemplate.of(uriMatchTemplate));
    }

    @Test
    void templatesOfDifferentLanguagesAreDifferent() {
        RouteTemplate uri = UriRouteTemplate.of("/items/{id:3}");
        RouteTemplate other = new StubTemplate("/items/{id:3}");
        // the same text in two languages
        assertNotEquals(uri, other);
        assertEquals(UriRouteTemplate.of("/items"), UriRouteTemplate.of("/items"));
        assertSame(UriRouteTemplate.class, UriRouteTemplate.PARSER.parse("/x").getClass());
    }

    @Test
    void theDefaultCountsOfTheVariablesAreTheCountsOfTheMatcher() {
        for (String template : TEMPLATES) {
            UriRouteTemplate uriTemplate = UriRouteTemplate.of(template);
            // the counts of RouteTemplate, from the variables
            RouteTemplate counted = new RouteTemplate() {
                @Override
                public String expression() {
                    return template;
                }

                @Override
                public List<RouteTemplateVariable> variables() {
                    return uriTemplate.variables();
                }

                @Override
                public @Nullable RouteCaptures match(String rawPath) {
                    return null;
                }

                @Override
                public RouteTemplate mount(String prefix) {
                    return this;
                }

                @Override
                public int rawLength() {
                    return 0;
                }
            };
            assertEquals(uriTemplate.pathVariableCount(), counted.pathVariableCount(), template);
            assertEquals(uriTemplate.patternVariableCount(), counted.patternVariableCount(), template);
        }
    }

    @Test
    void aSubclassOfUriMatchTemplateMatchesWithItsOwnRules() {
        UriRouteTemplate typed = UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class));
        assertNull(typed.matcher());
        assertNotNull(typed.match("/items/5"));
        assertNull(typed.match("/items/abc"));
        assertNotNull(UriRouteTemplate.of("/items/{id}").matcher());
    }

    @Test
    void aSubclassOfUriMatchTemplateHasNoRequiredPrefixAndIsOnlyEqualToItself() {
        UriRouteTemplate typed = UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class));
        // its rules may match paths the prefix of its template does not start
        assertEquals("", typed.requiredPrefix());
        assertEquals("/items/", UriRouteTemplate.of("/items/{id}").requiredPrefix());
        RouteTemplate untyped = UriRouteTemplate.of("/items/{id}");
        RouteTemplate other = UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class));
        assertNotEquals(untyped, typed);
        assertNotEquals(other, typed);
        assertEquals(typed, typed);
    }

    @Test
    void theVariablesOfAUriTemplateAreItsOccurrences() {
        List<RouteTemplateVariable> variables = UriRouteTemplate.of("/x/{id}/{id:\\d+}").variables();
        assertEquals(2, variables.size());
        // the pattern of each occurrence
        assertFalse(variables.get(0).pattern());
        assertTrue(variables.get(1).pattern());
        // one value per name: the value of the last occurrence, for every occurrence
        RouteCaptures captures = UriRouteTemplate.of("/x/{id}/{id}").match("/x/a/b");
        assertNotNull(captures);
        assertEquals(Arrays.asList("b", "b"), captures.values());
    }

    @Test
    void capturesOfTakeTheListsAsTheyAre() {
        List<RouteTemplateVariable> variables = List.of(RouteTemplateVariable.path("id"));
        List<String> values = List.of("5");
        RouteCaptures captures = RouteCaptures.of("/x/5", variables, values);
        assertSame(variables, captures.variables());
        assertEquals(values, captures.values());
        assertThrows(IllegalArgumentException.class, () -> RouteCaptures.of("/x", variables, List.of()));
    }

    @Test
    void theProjectionHasTheValueOfTheFirstOccurrenceOfAName() {
        List<RouteTemplateVariable> variables = List.of(
            RouteTemplateVariable.of("id", true, RouteTemplateVariable.Location.PATH, false, false),
            RouteTemplateVariable.of("id", true, RouteTemplateVariable.Location.PATH, false, false));
        RouteCaptures captures = RouteCaptures.copyOf("/x", variables, Arrays.asList(null, "second"));
        assertNull(captures.value("id"));
        assertFalse(captures.toUriMatchInfo().getVariableValues().containsKey("id"));
    }

    @Test
    void aMicronautTemplateCountsPatternVariablesLikeTheMatcher() {
        for (String template : TEMPLATES) {
            RouteTemplate parsed = UriRouteTemplate.of(template);
            UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(template).getTemplateString());
            assertEquals(matcher.getPatternVariableCount(), parsed.patternVariableCount(), template);
        }
        // a regular expression counts, the numeric modifier of a maximum length does not
        assertEquals(1, UriRouteTemplate.of("/books/{id:\\d+}").patternVariableCount());
        assertEquals(0, UriRouteTemplate.of("/books/{id:3}").patternVariableCount());
    }

    @Test
    void capturesProjectToUriMatchInfo() {
        List<RouteTemplateVariable> variables = List.of(
            RouteTemplateVariable.path("a"),
            RouteTemplateVariable.of("b", true, RouteTemplateVariable.Location.PATH, false, false),
            RouteTemplateVariable.of("q", true, RouteTemplateVariable.Location.QUERY, true, false),
            RouteTemplateVariable.path("a"));
        RouteCaptures captures = RouteCaptures.copyOf("/x/y", variables, Arrays.asList("1", null, "3", "4"));
        UriMatchInfo info = captures.toUriMatchInfo();
        // the first occurrence of a name, absent values left out
        assertEquals(Map.of("a", "1", "q", "3"), info.getVariableValues());
        assertTrue(info.getVariableMap().get("b").isOptional());
        assertTrue(info.getVariableMap().get("q").isQuery());
        assertTrue(info.getVariableMap().get("q").isExploded());
        assertFalse(info.getVariableMap().get("a").isOptional());
        assertEquals("1", captures.value("a"));
        assertThrows(IllegalArgumentException.class, () -> RouteCaptures.copyOf("/", variables, List.of("1")));
    }

    @Test
    void theVariableMapHasTheLastVariableOfANameInTheOrderOfTheFirst() {
        List<RouteTemplateVariable> variables = List.of(
            RouteTemplateVariable.path("a"),
            RouteTemplateVariable.path("b"),
            RouteTemplateVariable.of("a", true, RouteTemplateVariable.Location.QUERY, true, false));
        List<UriMatchVariable> described = RouteCaptures.uriMatchVariables(variables);
        Map<String, UriMatchVariable> byName = RouteCaptures.uriMatchVariableMap(described);
        assertEquals(List.of("a", "b"), new ArrayList<>(byName.keySet()));
        assertSame(described.get(2), byName.get("a"));
        assertTrue(byName.get("a").isExploded());
        assertThrows(UnsupportedOperationException.class, () -> byName.put("c", described.get(1)));

        RouteCaptures first = RouteCaptures.copyOf("/1/2", variables, Arrays.asList("1", "2", null));
        RouteCaptures second = RouteCaptures.copyOf("/3/4", variables, Arrays.asList("3", "4", null));
        UriMatchInfo firstInfo = first.toUriMatchInfo(described, byName);
        UriMatchInfo secondInfo = second.toUriMatchInfo(described, byName);
        assertSame(byName, firstInfo.getVariableMap());
        assertSame(byName, secondInfo.getVariableMap());
        assertEquals(Map.of("a", "1", "b", "2"), firstInfo.getVariableValues());
        assertEquals(Map.of("a", "3", "b", "4"), secondInfo.getVariableValues());
        // without the described variables, a map of the same variables
        assertEquals(byName, first.toUriMatchInfo().getVariableMap());
    }

    @Test
    void aTemplateMatchesTheLongestPrefixThatEndsBeforeASlash() {
        RouteTemplate orders = new StubTemplate("/orders/5");
        RouteCaptures captures = orders.matchPrefix("/orders/5/items/3");
        assertNotNull(captures);
        assertEquals("/orders/5", captures.path());
        // the whole path, with a trailing slash too
        assertEquals("/orders/5", orders.matchPrefix("/orders/5").path());
        // not a prefix that ends in the middle of a segment
        assertNull(orders.matchPrefix("/orders/55/items"));
        assertNull(orders.matchPrefix("/orders"));
        // the root matches the root of every path
        assertEquals("/", new StubTemplate("/").matchPrefix("/a/b").path());
        assertEquals("/", new StubTemplate("/").matchPrefix("/").path());
        // a long path of short segments that does not match
        assertNull(orders.matchPrefix("/x" + "/s".repeat(5_000)));
    }

    @Test
    void thePathOfAPrefixMatchIsThePrefixAsGiven() {
        // a template that gives the captures another path, e.g. without the matrix parameters
        RouteTemplate matrix = new MatrixTemplate("/orders");
        RouteCaptures captures = matrix.matchPrefix("/orders;v=1/items");
        assertNotNull(captures);
        assertEquals("/orders;v=1", captures.path());
    }

    @Test
    void theUriTemplatesOfBothFactoriesThatMatchTheSamePathsAreEqual() {
        RouteTemplate expression = UriRouteTemplate.of("/items/");
        RouteTemplate matchTemplate = UriRouteTemplate.of(new UriMatchTemplate("/items/"));
        assertEquals(expression, matchTemplate);
        assertEquals(expression.hashCode(), matchTemplate.hashCode());
        // the expression is kept as it was given
        assertEquals("/items/", expression.expression());
        assertNotEquals(UriRouteTemplate.of("/items/{id}"), expression);
    }

    @Test
    void theMatchesOfAUriTemplateShareItsVariableMap() {
        UriTemplateMatcher matcher = new UriTemplateMatcher("/items/{id}{?q*}");
        UriMatchInfo first = matcher.tryMatch("/items/1");
        UriMatchInfo second = matcher.tryMatch("/items/2");
        assertNotNull(first);
        assertNotNull(second);
        assertSame(first.getVariableMap(), second.getVariableMap());
        assertEquals(List.of("id", "q"), new ArrayList<>(first.getVariableMap().keySet()));
        assertTrue(first.getVariableMap().get("q").isExploded());
        assertEquals(Map.of("id", "1"), first.getVariableValues());
        assertEquals(Map.of("id", "2"), second.getVariableValues());
        assertThrows(UnsupportedOperationException.class, () -> first.getVariableMap().remove("id"));
    }

    /**
     * A template of another language, matching its expression literally.
     *
     * @param expression The expression
     */
    private record StubTemplate(String expression) implements RouteTemplate {
        @Override
        public List<RouteTemplateVariable> variables() {
            return List.of();
        }

        @Override
        public @Nullable RouteCaptures match(String path) {
            return path.equals(expression) ? RouteCaptures.copyOf(path, List.of(), new ArrayList<>()) : null;
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return new StubTemplate(prefix + expression);
        }

        @Override
        public String requiredPrefix() {
            return "";
        }

        @Override
        public int rawLength() {
            return expression.length();
        }

        @Override
        public int pathVariableCount() {
            return 0;
        }

        @Override
        public int patternVariableCount() {
            return 0;
        }
    }

    /**
     * A template that ignores the matrix parameter {@code ;v=1}: its captures have the path without it.
     *
     * @param expression The expression, matched literally
     */
    private record MatrixTemplate(String expression) implements RouteTemplate {
        @Override
        public List<RouteTemplateVariable> variables() {
            return List.of();
        }

        @Override
        public @Nullable RouteCaptures match(String path) {
            String withoutMatrix = path.replace(";v=1", "");
            return withoutMatrix.equals(expression) ? RouteCaptures.copyOf(withoutMatrix, List.of(), List.of()) : null;
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return new MatrixTemplate(prefix + expression);
        }

        @Override
        public int rawLength() {
            return expression.length();
        }
    }
}
