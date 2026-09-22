package io.micronaut.web.router.processor;

import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated parser of a plan agrees with the matcher of the engine of every compiled slot:
 * it reports a slot exactly when the engine matches the path, with the same raw values. The
 * templates and paths are the ones of the differential tests of the route index.
 */
class RoutePlanDifferentialTest {

    private static final String[] TEMPLATES = {
        "/", "", "/books", "/books/", "/books/{id}", "/books/{id}/authors", "/books/{id:\\d+}",
        "/books{/id}", "/books{.ext}", "/books{?q}", "/books/{+path}", "/files/{+path}", "/files",
        "/{name}", "/{name}/details", "/b", "/bo", "/boo", "/api/v1/users", "/api/v1/users/{id}",
        "/api/v2/{+rest}", "/api", "/a/b/c", "/a/{b}/c", "/a/b{c}", "/x-{id}", "/foo{bar}", "/café/{x}",
        "/pets/{id}/owners/{owner}", "/{a}/{b}", "/{a}/b", "/a/{b}", "/items/{id}{?q,r}", "/items/{id}/x{?q}"
    };

    private static final String[] PATHS = {
        "/", "", "/books", "/books/", "/books/12", "/books/abc", "/books/12/authors", "/books/12/authors/",
        "/books.json", "/books?q=1", "/books/12?x=/y", "/books/a/b/c", "/files", "/files/", "/files/a/b",
        "/foo", "/foo/details", "/b", "/bo", "/boo", "/booo", "/api/v1/users", "/api/v1/users/7",
        "/api/v2/x/y", "/api", "/a/b/c", "/a/x/c", "/a/bz", "/x-1", "/foobar", "/café/1", "/?", "//", "/books//",
        "/pets/1/owners/2", "/pets/a+b/owners/2", "/pets/a%20b/owners/c;d", "/a/b", "/x/b", "/a/{}", "/a/#",
        "/a/b#", "/items/1?q=2", "/items/1/x", "/items/1/x/", "/items/1/x?q=/"
    };

    @Test
    void theParserAgreesWithTheEngine() {
        check(TEMPLATES, PATHS);
    }

    @Test
    void randomTemplatesAndPaths() {
        Random random = new Random(42);
        String[] pieces = {"/", "a", "b", "ab", "{x}", "{y:\\d+}", "{+p}", "{.e}", "{/s}", "{?q}", "-", "1", "12", "/{v}", "/{w}/"};
        for (int round = 0; round < 200; round++) {
            List<String> templates = new ArrayList<>();
            int count = 1 + random.nextInt(20);
            while (templates.size() < count) {
                String template = random(random, pieces, true);
                try {
                    UriTemplateMatcher.of(template);
                    templates.add(template);
                } catch (RuntimeException e) {
                    // not a valid template, try another one
                }
            }
            String[] paths = new String[50];
            for (int i = 0; i < paths.length; i++) {
                paths[i] = random(random, new String[] {"/", "a", "b", "ab", "1", "12", "-", ".", "?q=1", "x", "+", ";", "%20", "#"}, false);
            }
            check(templates.toArray(String[]::new), paths);
        }
    }

    @Test
    void someTemplatesAreCompiledAndSomeAreNot() {
        CompiledRoutePlan plan = plan(TEMPLATES);
        long compiled = plan.slots().stream().filter(RouteSlot::compiled).count();
        assertTrue(compiled > 15, "compiled: " + compiled);
        assertTrue(compiled < TEMPLATES.length, "compiled: " + compiled);
    }

    private static void check(String[] templates, String[] paths) {
        CompiledRoutePlan compiled = plan(templates);
        RoutePlan plan = GeneratedPlans.load(compiled);
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        for (String path : paths) {
            Map<String, List<String>> matched = GeneratedPlans.match(plan, UriTemplateMatcher.normalizeForMatching(path));
            for (int i = 0; i < templates.length; i++) {
                RouteSlot slot = compiled.slots().get(i);
                if (!slot.compiled()) {
                    assertFalse(matched.containsKey(slot.key()), "a slot the parser does not compile was reported");
                    continue;
                }
                RouteCaptures captures = engines.matcher(engines.parse(slot.template())).match(path);
                String template = templates[i];
                if (captures == null) {
                    assertFalse(matched.containsKey(slot.key()), () -> "the parser matches " + path + " with " + template + ", the engine does not");
                } else {
                    assertTrue(matched.containsKey(slot.key()), () -> "the engine matches " + path + " with " + template + ", the parser does not");
                    List<String> expected = new ArrayList<>();
                    for (int v = 0; v < captures.variables().size(); v++) {
                        if (captures.variables().get(v).location() == RouteTemplateVariable.Location.PATH) {
                            expected.add(captures.values().get(v));
                        }
                    }
                    assertEquals(expected, matched.get(slot.key()), () -> "the values of " + template + " for " + path);
                }
            }
        }
    }

    private static CompiledRoutePlan plan(String[] templates) {
        List<RouteDescription> routes = new ArrayList<>();
        for (int i = 0; i < templates.length; i++) {
            routes.add(GeneratedPlans.route("test:" + i, "GET", RouteTemplate.micronaut(templates[i])));
        }
        return new RoutePlanCompiler().plan("test.$Differential$RoutePlan", "test:differential", List.of(), routes);
    }

    private static String random(Random random, String[] pieces, boolean leadingSlash) {
        StringBuilder builder = new StringBuilder(leadingSlash || random.nextBoolean() ? "/" : "");
        int n = random.nextInt(5);
        for (int i = 0; i < n; i++) {
            builder.append(pieces[random.nextInt(pieces.length)]);
        }
        return builder.toString();
    }
}
