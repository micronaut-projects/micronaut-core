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

import io.micronaut.http.uri.UriTemplateMatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The index must never skip a route that matches: its candidates must include every template
 * whose own matching accepts the path.
 */
class RouteIndexTest {

    private static final String[] TEMPLATES = {
        "/", "", "/books", "/books/", "/books/{id}", "/books/{id}/authors", "/books/{id:\\d+}",
        "/books{/id}", "/books{.ext}", "/books{?q}", "/books/{+path}", "/files/{+path}", "/files",
        "/{name}", "/{name}/details", "/b", "/bo", "/boo", "/api/v1/users", "/api/v1/users/{id}",
        "/api/v2/{+rest}", "/api", "/a/b/c", "/a/{b}/c", "/a/b{c}", "/x-{id}", "/foo{bar}", "/café/{x}"
    };

    private static final String[] PATHS = {
        "/", "", "/books", "/books/", "/books/12", "/books/abc", "/books/12/authors", "/books/12/authors/",
        "/books.json", "/books?q=1", "/books/12?x=/y", "/books/a/b/c", "/files", "/files/", "/files/a/b",
        "/foo", "/foo/details", "/b", "/bo", "/boo", "/booo", "/api/v1/users", "/api/v1/users/7",
        "/api/v2/x/y", "/api", "/a/b/c", "/a/x/c", "/a/bz", "/x-1", "/foobar", "/café/1", "/?", "//", "/books//"
    };

    @Test
    void candidatesIncludeEveryMatchingTemplate() {
        check(TEMPLATES, PATHS);
    }

    @Test
    void randomTemplatesAndPaths() {
        Random random = new Random(42);
        String[] pieces = {"/", "a", "b", "ab", "{x}", "{y:\\d+}", "{+p}", "{.e}", "{/s}", "{?q}", "-", "1", "12"};
        for (int round = 0; round < 200; round++) {
            String[] templates = new String[1 + random.nextInt(20)];
            for (int i = 0; i < templates.length; i++) {
                templates[i] = randomTemplate(random, pieces);
            }
            String[] paths = new String[50];
            for (int i = 0; i < paths.length; i++) {
                paths[i] = random(random, new String[] {"/", "a", "b", "ab", "1", "12", "-", ".", "?q=1", "x"}, false);
            }
            check(templates, paths);
        }
    }

    @Test
    void literalPrefixesPrune() {
        String[] templates = {"/books/{id}", "/authors/{id}", "/{name}", "/books"};
        RouteIndex index = index(templates);
        assertArrayEquals(new int[] {0, 2, 3}, index.candidates("/books/12"));
        assertArrayEquals(new int[] {1, 2}, index.candidates("/authors/1"));
        assertArrayEquals(new int[] {2}, index.candidates("/other"));
    }

    private static void check(String[] templates, String[] paths) {
        RouteIndex index = index(templates);
        UriTemplateMatcher[] matchers = Arrays.stream(templates).map(UriTemplateMatcher::of).toArray(UriTemplateMatcher[]::new);
        for (String path : paths) {
            int[] candidates = index.candidates(path);
            for (int i = 1; i < candidates.length; i++) {
                assertTrue(candidates[i - 1] < candidates[i], "candidates are not in route order");
            }
            List<Integer> candidateList = new ArrayList<>();
            for (int candidate : candidates) {
                candidateList.add(candidate);
            }
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].tryMatch(path) != null) {
                    int route = i;
                    assertTrue(candidateList.contains(route), () -> "template " + templates[route] + " matches " + path + " but is not a candidate: " + candidateList);
                }
            }
        }
    }

    private static RouteIndex index(String[] templates) {
        return RouteIndex.build(Arrays.stream(templates).map(t -> UriTemplateMatcher.of(t).getRequiredPrefix()).toArray(String[]::new));
    }

    private static String randomTemplate(Random random, String[] pieces) {
        while (true) {
            String template = random(random, pieces, true);
            try {
                UriTemplateMatcher.of(template);
                return template;
            } catch (RuntimeException e) {
                // not a valid template, try another one
            }
        }
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
