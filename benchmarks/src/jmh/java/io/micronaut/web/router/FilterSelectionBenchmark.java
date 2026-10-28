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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.FilterChain;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpFilter;
import org.reactivestreams.Publisher;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;
import java.util.function.Supplier;

/** Selection only: filter execution and response encoding are outside this benchmark. */
@State(Scope.Thread)
public class FilterSelectionBenchmark {
    @Param({"NONE", "MOSTLY_REJECTED", "MOSTLY_MATCHED", "DYNAMIC"})
    public String scenario;

    @Param({"1", "8", "32"})
    public int filters = 32;

    private Router router;
    private HttpRequest<?> request;
    private Supplier<Router> factory;

    @Setup
    public void setup() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        var routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/items/1", (r, v) -> HttpResponse.ok());
        if (!scenario.equals("NONE")) {
            for (int i = 0; i < filters; i++) {
                boolean matched = scenario.equals("MOSTLY_MATCHED") || i % 8 == 0;
                routes.serverFilter("/items/*").methods(matched ? HttpMethod.GET : HttpMethod.POST)
                    .order(filters - i).before(r -> { });
            }
        }
        routes.close();
        factory = scenario.equals("DYNAMIC") ? () -> dynamic(filters) : () -> new DefaultRouter(List.of(), List.of(() -> assembly));
        router = factory.get();
        request = HttpRequest.GET("/items/1");
        int expected = scenario.equals("NONE") ? 0 : scenario.equals("MOSTLY_REJECTED") ? (filters + 7) / 8 : filters;
        if (router.findFilters(request, null).size() != expected) {
            throw new IllegalStateException("Unexpected selected filters");
        }
    }

    private static Router dynamic(int count) {
        var builder = new DefaultRouteBuilder(null) { };
        for (int i = 0; i < count; i++) {
            int order = count - i;
            HttpFilter bean = new HttpFilter() {
                @Override
                public int getOrder() { return order; }
                @Override
                public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                    return chain.proceed(request);
                }
            };
            GenericHttpFilter filter = GenericHttpFilter.createLegacyFilter(bean, new FilterOrder.Dynamic(0));
            builder.getFilterRoutes().add(new DefaultFilterRoute("/items/*", () -> filter, AnnotationMetadata.EMPTY_METADATA, false).methods(HttpMethod.GET));
        }
        return new DefaultRouter(builder);
    }

    @Benchmark
    public Object select() {
        return router.findFilters(request, null);
    }

    @Benchmark
    public Object prepareAndSelect() {
        var fresh = factory.get();
        fresh.findFilters(request, null);
        return fresh;
    }
}
