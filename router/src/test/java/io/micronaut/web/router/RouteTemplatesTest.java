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
import io.micronaut.http.uri.UriRouteTemplate;
import io.micronaut.http.uri.RouteTemplateVariable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The router checks that a template of another kind describes the facts of the Micronaut route
 * selection policy, see {@link RouteTemplates#checked(RouteTemplate)}.
 */
class RouteTemplatesTest {

    @Test
    void aWellDescribedTemplateIsAccepted() {
        RouteTemplate template = new StubTemplate("/a/", 3, 0, 0);
        assertSame(template, RouteTemplates.checked(template));
        RouteTemplate micronaut = UriRouteTemplate.of("/a/{b}");
        assertSame(micronaut, RouteTemplates.checked(micronaut));
    }

    @Test
    void theRequiredPrefixStartsWithASlash() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RouteTemplates.checked(new StubTemplate("a", 3, 0, 0)));
        assertTrue(e.getMessage().contains("does not start with a slash"), e.getMessage());
        assertTrue(e.getMessage().contains("StubTemplate"), e.getMessage());
    }

    @Test
    void theCountsAreNotNegative() {
        assertThrows(IllegalStateException.class, () -> RouteTemplates.checked(new StubTemplate("/a/", -1, 0, 0)));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RouteTemplates.checked(new StubTemplate("/a/", 3, 0, -1)));
        assertTrue(e.getMessage().contains("a negative pattern variable count"), e.getMessage());
    }

    @Test
    void thePatternVariablesAreVariablesOfThePath() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RouteTemplates.checked(new StubTemplate("/a/", 3, 0, 1)));
        assertTrue(e.getMessage().contains("greater than the path variable count"), e.getMessage());
    }

    /**
     * A template whose facts a test sets.
     */
    private record StubTemplate(String requiredPrefix, int rawLength, int pathVariableCount, int patternVariableCount) implements RouteTemplate {
        @Override
        public String expression() {
            return "/a/";
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            return List.of();
        }

        @Override
        public @Nullable RouteCaptures match(String path) {
            return null;
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return this;
        }

    }
}
