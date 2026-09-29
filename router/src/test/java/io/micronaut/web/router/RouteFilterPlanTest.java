package io.micronaut.web.router;

import io.micronaut.core.util.PathMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A static decision of the filter plan for an Ant pattern agrees with the Ant matcher for every
 * path that starts with the literal start of the route template.
 */
class RouteFilterPlanTest {

    private static final List<String> PATTERNS = List.of(
        "/**", "/api/**", "/api/v1/**", "/api", "/api/*", "/api/*/x", "/api/**/x", "/admin/**", "/ap*/**",
        "/a?i/**", "api/**", "//api/**", "/api//**", "/api/books/**", "/api/books", "/api/books/*",
        "/**/books", "/*/books/**", "/api/**/**", "/API/**", "/ api/**", "/api/b*/**", "/other"
    );
    private static final List<String> PREFIXES = List.of(
        "/", "/api/", "/api", "/api/books/", "/api/books", "/api/b", "/admin/", "/apix/", "/api/v1/", "//api/", "/api//books/", "api/"
    );
    private static final List<String> SUFFIXES = List.of(
        "", "1", "1/", "x", "1/x", "books/1", "books/1/", "a/b/c", "/", "//", "books", "v1/x", " a/x"
    );

    @Test
    void staticDecisionsAgreeWithTheMatcher() {
        int decided = 0;
        for (String pattern : PATTERNS) {
            for (String prefix : PREFIXES) {
                byte decision = RouteFilterPlan.decideAntPattern(pattern, prefix);
                if (decision == RouteFilterPlan.CHECK) {
                    continue;
                }
                decided++;
                for (String suffix : SUFFIXES) {
                    String path = prefix + suffix;
                    boolean matches = PathMatcher.ANT.matches(pattern, path);
                    assertEquals(decision == RouteFilterPlan.MATCH, matches,
                        () -> "pattern " + pattern + ", prefix " + prefix + ", path " + path);
                }
            }
        }
        assertTrue(decided > 100, "decided " + decided);
    }

    @Test
    void commonPatternsAreDecided() {
        assertEquals(RouteFilterPlan.MATCH, RouteFilterPlan.decideAntPattern("/**", "/api/books/"));
        assertEquals(RouteFilterPlan.MATCH, RouteFilterPlan.decideAntPattern("/api/**", "/api/books/"));
        assertEquals(RouteFilterPlan.MATCH, RouteFilterPlan.decideAntPattern("/api/**", "/api/"));
        assertEquals(RouteFilterPlan.NO_MATCH, RouteFilterPlan.decideAntPattern("/admin/**", "/api/books/"));
        assertEquals(RouteFilterPlan.NO_MATCH, RouteFilterPlan.decideAntPattern("/api/books", "/api/books/1/"));
        assertEquals(RouteFilterPlan.CHECK, RouteFilterPlan.decideAntPattern("/api/*", "/api/"));
        assertEquals(RouteFilterPlan.CHECK, RouteFilterPlan.decideAntPattern("/api/books/**", "/api/b"));
        assertFalse(RouteFilterPlan.decideAntPattern("/**", "books/") == RouteFilterPlan.MATCH);
    }
}
