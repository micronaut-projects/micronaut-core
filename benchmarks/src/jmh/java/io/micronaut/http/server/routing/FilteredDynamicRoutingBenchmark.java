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
package io.micronaut.http.server.routing;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteResolution;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;

/**
 * Filtered resolution with a fresh request per operation, including locator-cache creation.
 * Body cases measure candidate preparation and selection, without body decoding or I/O.
 */
@State(Scope.Thread)
public class FilteredDynamicRoutingBenchmark {

    @Param({"PLAIN", "FILTERED_LOCATOR", "NESTED_LOCATOR", "LOCATED_BODY"})
    public String scenario;

    private Router router;
    private String path;

    @Setup
    public void setUp() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        LocatedRoutes<String> items = new LocatedRoutes<>() {
            @Override
            public Argument<String> targetType() {
                return Argument.STRING;
            }

            @Override
            public void routes(LocatedHttpRouteBuilder<String> located) {
                if (scenario.equals("LOCATED_BODY")) {
                    located.POST("/items/special").consumesAll().where(RequestCondition.bodyText("express"::equals))
                        .handle((request, variables, target) -> HttpResponse.ok("special"));
                }
                located.POST("/items/{name}").consumesAll().attribute("winner", true)
                    .handle((request, variables, target) -> HttpResponse.ok(target));
            }
        };
        path = "/shops/1/items/special";
        if (scenario.equals("PLAIN")) {
            routes.POST("/shops/{id}/items/{name}").consumesAll().attribute("winner", true)
                .handle((request, variables) -> HttpResponse.ok("plain"));
        } else if (scenario.equals("NESTED_LOCATOR")) {
            LocatedRoutes<String> shops = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> located) {
                    located.locate("/shops/{id}", (request, variables) -> "shop", items);
                }
            };
            routes.locate("/tenants/{tenant}", (request, variables) -> "tenant", shops);
            path = "/tenants/acme" + path;
        } else {
            routes.locate("/shops/{id}", (request, variables) -> "shop", items);
        }
        routes.close();
        router = new DefaultRouter(List.of(), List.of(() -> assembly));
        UriRouteMatch<Object, Object> match = resolve();
        if (match == null || !Boolean.TRUE.equals(match.getRouteInfo().getAttributes().get("winner"))
            || !"special".equals(match.getVariableValues().get("name"))) {
            throw new IllegalStateException("Unexpected route for " + scenario);
        }
    }

    @Benchmark
    public UriRouteMatch<Object, Object> resolve() {
        HttpRequest<?> request = HttpRequest.POST(path, "ordinary").contentType(MediaType.TEXT_PLAIN_TYPE);
        RouteResolution<Object, Object> resolution = scenario.equals("PLAIN")
            ? router.resolve(request) : router.resolve(request, match -> true);
        if (resolution instanceof RouteResolution.Pending<Object, Object> pending) {
            var ranks = pending.pending().getRanks();
            // The ordinary body rejects the specific route: consume the selected fallback match.
            resolution = pending.pending().choose(request, ranks.get(ranks.size() - 1));
        }
        return resolution.matchOrNull();
    }
}
