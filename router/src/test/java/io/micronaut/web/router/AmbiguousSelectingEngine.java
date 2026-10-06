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
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.spi.RouteMatchSelector;

import java.util.List;

/**
 * A route selector that selects every match.
 */
public final class AmbiguousSelectingEngine extends ColonRouteTemplateEngine implements RouteMatchSelector {

    public static final String ID = "test.colon.ambiguous";

    public AmbiguousSelectingEngine() {
        super(ID);
    }

    public static RouteTemplate template(String expression) {
        return RouteTemplate.of(ID, expression);
    }

    @Override
    public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
        return matches.stream().map(Selection::of).toList();
    }
}
