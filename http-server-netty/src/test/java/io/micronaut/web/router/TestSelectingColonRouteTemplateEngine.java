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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.spi.RouteMatchSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * The colon language with a simple route selector, like JAX-RS: the first accepted type, by
 * quality, that a route produces, in the order of the routes. Without an {@code Accept} header
 * every type is accepted. A route whose template has a variable named {@code rejected} is never
 * selected, as JAX-RS does not select the method of a less specific root resource class.
 */
public final class TestSelectingColonRouteTemplateEngine extends TestColonRouteTemplateEngine implements RouteMatchSelector {

    public static final String ID = "test.colon.selecting";

    public TestSelectingColonRouteTemplateEngine() {
        super(ID);
    }

    public static RouteTemplate template(String expression) {
        return RouteTemplate.of(ID, expression);
    }

    @Override
    public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
        List<MediaType> accepted = new ArrayList<>(request.accept());
        if (accepted.isEmpty()) {
            accepted.add(MediaType.ALL_TYPE);
        }
        for (MediaType accept : accepted) {
            for (UriRouteMatch<?, ?> match : matches) {
                if (isRejected(match)) {
                    continue;
                }
                List<MediaType> produces = match.getRouteInfo().getProduces();
                for (MediaType produced : produces.isEmpty() ? List.of(MediaType.APPLICATION_JSON_TYPE) : produces) {
                    if (accept.matches(produced)) {
                        return List.of(Selection.of(match, produced.equals(MediaType.ALL_TYPE) ? MediaType.APPLICATION_JSON_TYPE : produced));
                    }
                }
            }
        }
        return List.of();
    }

    /**
     * @param match A match
     * @return Whether the route of the match is never selected
     */
    public static boolean isRejected(UriRouteMatch<?, ?> match) {
        return match.getRouteInfo().getRouteTemplate().expression().contains(":rejected");
    }
}
