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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.FilterChain;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpFilter;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreparedApplicationFiltersTest {
    @Test
    void fixedSelectionKeepsEqualOrderCategoriesMethodsPathsAndLazyCreation() {
        var builder = new DefaultRouteBuilder(null) { };
        AtomicInteger created = new AtomicInteger();
        GenericHttpFilter always = fixed(0);
        GenericHttpFilter conditional = fixed(0);
        GenericHttpFilter specific = fixed(-10);
        GenericHttpFilter post = fixed(-20);
        builder.getFilterRoutes().add(route("/items/*", conditional, 0, created));
        builder.getFilterRoutes().add(route("/**", always, 0, created));
        builder.getFilterRoutes().add(route("/items/a", specific, -10, created));
        builder.getFilterRoutes().add(route("/items/*", post, -20, created).methods(HttpMethod.POST));
        Router router = new DefaultRouter(builder);
        assertEquals(0, created.get());
        assertEquals(List.of(always), router.findFilters(HttpRequest.GET("/other"), null));
        assertEquals(1, created.get());
        assertEquals(List.of(always, conditional), router.findFilters(HttpRequest.GET("/items/b"), null));
        assertEquals(2, created.get());
        assertEquals(List.of(specific, always, conditional), router.findFilters(HttpRequest.GET("/items/a"), null));
        assertEquals(3, created.get());
        assertEquals(List.of(post, specific, always, conditional), router.findFilters(HttpRequest.POST("/items/a", ""), null));
        assertEquals(4, created.get());
        assertEquals(List.of(always, conditional), router.findFilters(HttpRequest.GET("/items/b")));
        assertEquals(List.of(always), router.findFilters(HttpRequest.OPTIONS("*"), null));

        var alone = new DefaultRouteBuilder(null) { };
        alone.getFilterRoutes().add(route("/**", always, 0, created));
        assertEquals(List.of(always), new DefaultRouter(alone).findFilters(HttpRequest.OPTIONS("*"), null));
    }

    @Test
    void dynamicOrdersAndEnablementAreReadForEverySelection() {
        var builder = new DefaultRouteBuilder(null) { };
        Dynamic first = new Dynamic(-1);
        Dynamic second = new Dynamic(1);
        GenericHttpFilter a = GenericHttpFilter.createLegacyFilter(first, new FilterOrder.Dynamic(0));
        GenericHttpFilter b = GenericHttpFilter.createLegacyFilter(second, new FilterOrder.Dynamic(0));
        builder.getFilterRoutes().add(new DefaultFilterRoute("/items/*", () -> a, AnnotationMetadata.EMPTY_METADATA, false));
        builder.getFilterRoutes().add(new DefaultFilterRoute("/items/*", () -> b, AnnotationMetadata.EMPTY_METADATA, false));
        Router router = new DefaultRouter(builder);
        assertEquals(List.of(a, b), router.findFilters(HttpRequest.GET("/items/a"), null));
        first.order = 2;
        assertEquals(List.of(b, a), router.findFilters(HttpRequest.GET("/items/a"), null));
        first.enabled = false;
        assertEquals(List.of(b), router.findFilters(HttpRequest.GET("/items/a"), null));
        first.enabled = true;
        first.order = 1;
        assertEquals(List.of(a, b), router.findFilters(HttpRequest.GET("/items/a"), null));
    }

    @Test
    void unpreparedFilterRoutesKeepTheirLiveMethodRestrictions() {
        var builder = new DefaultRouteBuilder(null) { };
        var filter = fixed(0);
        var route = new DefaultFilterRoute("/items/*", () -> filter, AnnotationMetadata.EMPTY_METADATA, false);
        route.methods(HttpMethod.POST);
        builder.getFilterRoutes().add(route);
        Router router = new DefaultRouter(builder);
        assertEquals(List.of(), router.findFilters(HttpRequest.GET("/items/a"), null));
        route.methods(HttpMethod.GET);
        assertEquals(List.of(filter), router.findFilters(HttpRequest.GET("/items/a"), null));
    }

    private static GenericHttpFilter fixed(int order) {
        return GenericHttpFilter.withOrder(GenericHttpFilter.createRouteRequestFilter((request, context) -> null, null), order);
    }

    private static DefaultFilterRoute route(String pattern, GenericHttpFilter filter, int order, AtomicInteger created) {
        return new DefaultFilterRoute(pattern, () -> {
            created.incrementAndGet();
            return filter;
        }, AnnotationMetadata.EMPTY_METADATA, false).fixedOrder(order);
    }

    private static final class Dynamic implements HttpFilter, Toggleable {
        int order;
        boolean enabled = true;

        Dynamic(int order) { this.order = order; }
        @Override
        public int getOrder() { return order; }
        @Override
        public boolean isEnabled() { return enabled; }
        @Override
        public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
            return chain.proceed(request);
        }
    }
}
