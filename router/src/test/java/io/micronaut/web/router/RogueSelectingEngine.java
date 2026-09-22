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
import java.util.Objects;

/**
 * A route selector that selects a match it was not given.
 */
public final class RogueSelectingEngine extends ColonRouteTemplateEngine implements RouteMatchSelector {

    public static final String ID = "test.colon.rogue";
    public static final RouteTemplate TEMPLATE = RouteTemplate.of(ID, "/rogue/:id");

    public RogueSelectingEngine() {
        super(ID);
    }

    @Override
    public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
        return List.of(Selection.of(Objects.requireNonNull(matches.get(0).getRouteInfo().tryMatch("/rogue/2"))));
    }
}
