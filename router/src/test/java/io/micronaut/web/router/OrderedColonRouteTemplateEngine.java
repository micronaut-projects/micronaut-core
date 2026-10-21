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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The colon language of {@link ColonRouteTemplateEngine} with the order of specificity of JAX-RS
 * (JAX-RS 3.1, section 3.7.2): more literal text first, then more variables, then more variables
 * with a regular expression.
 */
public class OrderedColonRouteTemplateEngine extends ColonRouteTemplateEngine implements RouteMatchSelector {

    public static final String ID = "test.colon.ordered";

    static final Comparator<RouteTemplate> ORDER = Comparator
        .comparingInt(RouteTemplate::rawLength).reversed()
        .thenComparing(Comparator.comparingInt(RouteTemplate::pathVariableCount).reversed())
        .thenComparing(Comparator.comparingInt(RouteTemplate::patternVariableCount).reversed());

    public OrderedColonRouteTemplateEngine() {
        super(ID);
    }

    protected OrderedColonRouteTemplateEngine(String id) {
        super(id);
    }

    public static final OrderedColonRouteTemplateEngine INSTANCE = new OrderedColonRouteTemplateEngine();

    public static RouteTemplate template(String expression) {
        return INSTANCE.parse(expression);
    }

    /**
     * The order of the routes: by the template a locator route was declared with, or by the
     * template of the route.
     */
    static final Comparator<UriRouteInfo<?, ?>> ROUTE_ORDER = Comparator.comparing(OrderedColonRouteTemplateEngine::declared, ORDER);

    static RouteTemplate declared(UriRouteInfo<?, ?> route) {
        RouteTemplate prefix = RouteMatchSelector.locatorPrefix(route);
        return prefix != null ? prefix : route.getRouteTemplate();
    }

    /**
     * Select the most specific matches by the order of the language: more than one is ambiguous.
     */
    @Override
    public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
        UriRouteInfo<?, ?> best = null;
        for (UriRouteMatch<?, ?> match : matches) {
            if (best == null || ROUTE_ORDER.compare(match.getRouteInfo(), best) < 0) {
                best = match.getRouteInfo();
            }
        }
        List<Selection> selected = new ArrayList<>();
        for (UriRouteMatch<?, ?> match : matches) {
            if (ROUTE_ORDER.compare(match.getRouteInfo(), Objects.requireNonNull(best)) == 0) {
                selected.add(Selection.of(match));
            }
        }
        return selected;
    }

    @Override
    public Optional<Comparator<UriRouteInfo<?, ?>>> order() {
        return Optional.of(ROUTE_ORDER);
    }
}
