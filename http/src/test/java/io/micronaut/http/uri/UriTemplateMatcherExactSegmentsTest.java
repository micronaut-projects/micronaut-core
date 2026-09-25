package io.micronaut.http.uri;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The exact structure of a template that compilers lower into generated parsers, and the rule of
 * a whole-segment variable, checked against {@link UriTemplateMatcher#tryMatch(String)}.
 */
class UriTemplateMatcherExactSegmentsTest {

    private static final RouteTemplateSegment VAR = RouteTemplateSegment.VARIABLE;

    @Test
    void literalAndWholeSegmentVariableTemplatesHaveExactSegments() {
        assertEquals(List.of(lit("pets")), new UriTemplateMatcher("/pets").exactPathSegments());
        assertEquals(List.of(lit("pets"), VAR), new UriTemplateMatcher("/pets/{id}").exactPathSegments());
        assertEquals(List.of(lit("pets"), VAR, lit("owners"), VAR), new UriTemplateMatcher("/pets/{id}/owners/{owner}").exactPathSegments());
        assertEquals(List.of(VAR, lit("x")), new UriTemplateMatcher("/{a}/x").exactPathSegments());
        // query variables do not take part in matching a path
        assertEquals(List.of(lit("search")), new UriTemplateMatcher("/search{?q}").exactPathSegments());
        assertEquals(List.of(lit("items"), VAR, lit("x")), new UriTemplateMatcher("/items/{id}/x{?a,b}").exactPathSegments());
        // a fragment expansion, like a query, matches the end of the path
        assertEquals(List.of(lit("a")), new UriTemplateMatcher("/a{#frag}").exactPathSegments());
    }

    @Test
    void otherTemplatesHaveNone() {
        for (String template : List.of("/pets/{id:3}", "/pets/{id:[0-9]+}", "/files/{+path}", "/own{/rest:.*}", "/x{/id}",
            "/pets/x{id}", "/pets/{id}.json", "/", "", "/pets/{a,b}", "/pets/", "/pets//x", "{id}", "/{?q}", "/a{.ext}", "/items/{id}{?q}")) {
            assertNull(new UriTemplateMatcher(template).exactPathSegments(), template);
        }
    }

    @Test
    void theVariableRuleIsTheRuleOfTheMatcher() {
        UriTemplateMatcher last = new UriTemplateMatcher("/p/{id}");
        UriTemplateMatcher middle = new UriTemplateMatcher("/p/{id}/x");
        List<String> values = new ArrayList<>();
        for (char c = 0x20; c < 0x7f; c++) {
            values.add(String.valueOf(c));
            values.add("a" + c);
            values.add(c + "a");
            values.add("a" + c + "b");
        }
        values.addAll(List.of("%20", "%2F", "a#{", "#", "a#", "é", "a\u0000b", "{}", ""));
        for (String value : values) {
            if (value.indexOf('/') >= 0 || value.indexOf('?') >= 0) {
                continue;
            }
            String path = "/p/" + value;
            boolean expected = last.tryMatch(path) != null;
            assertEquals(expected, UriTemplateMatcher.acceptsSegmentVariable(path, 3, path.length()), path);
            String nested = "/p/" + value + "/x";
            boolean nestedExpected = middle.tryMatch(nested) != null;
            assertEquals(nestedExpected, UriTemplateMatcher.acceptsSegmentVariable(nested, 3, 3 + value.length()), nested);
        }
        // known results, not only agreement
        assertEquals(false, UriTemplateMatcher.acceptsSegmentVariable("/p/", 3, 3));
        assertEquals(false, UriTemplateMatcher.acceptsSegmentVariable("/p/a+b", 3, 6));
        assertEquals(true, UriTemplateMatcher.acceptsSegmentVariable("/p/a%20b", 3, 8));
        assertEquals(true, UriTemplateMatcher.acceptsSegmentVariable("/p/a#", 3, 5));
        assertEquals(false, UriTemplateMatcher.acceptsSegmentVariable("/p/a#/x", 3, 5));
    }

    private static RouteTemplateSegment lit(String text) {
        return RouteTemplateSegment.literal(text);
    }
}
