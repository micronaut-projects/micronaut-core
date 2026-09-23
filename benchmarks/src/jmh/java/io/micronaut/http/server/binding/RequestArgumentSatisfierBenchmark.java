/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.UriRouteMatch;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Measures argument binding alone: the route is found once in the setup, and each invocation
 * creates the match for a fresh request, runs the binders that run before and after the filters
 * and invokes the method. {@link #request()} measures the request creation, which {@link #bind()}
 * includes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RequestArgumentSatisfierBenchmark {

    @Param
    Scenario scenario;

    ApplicationContext applicationContext;
    RequestArgumentSatisfier requestArgumentSatisfier;
    UriRouteInfo<Object, Object> routeInfo;

    @Setup
    public void setup() {
        applicationContext = ApplicationContext.run();
        requestArgumentSatisfier = applicationContext.getBean(RequestArgumentSatisfier.class);
        Router router = applicationContext.getBean(Router.class);
        UriRouteMatch<Object, Object> match = Objects.requireNonNull(router.findClosest(scenario.request()), "No route for " + scenario);
        routeInfo = match.getRouteInfo();
        Object result = bind();
        if (!scenario.expected.equals(result)) {
            throw new IllegalStateException("Unexpected result " + result + " for " + scenario);
        }
    }

    @TearDown
    public void tearDown() {
        applicationContext.close();
    }

    @Benchmark
    public Object bind() {
        MutableHttpRequest<?> request = scenario.request();
        UriRouteMatch<Object, Object> routeMatch = Objects.requireNonNull(routeInfo.tryMatch(request.getPath()));
        // as the server does after the match, @PathVariable binders read the match from the request
        RouteAttributes.setRouteMatch(request, routeMatch);
        RouteAttributes.setRouteInfo(request, routeInfo);
        requestArgumentSatisfier.fulfillArgumentRequirementsBeforeFilters(routeMatch, request);
        requestArgumentSatisfier.fulfillArgumentRequirementsAfterFilters(routeMatch, request);
        return routeMatch.execute();
    }

    @Benchmark
    public Object request() {
        return scenario.request();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + RequestArgumentSatisfierBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    public enum Scenario {
        /**
         * Two path variables.
         */
        PATH("bar is 10") {
            @Override
            MutableHttpRequest<?> request() {
                return HttpRequest.GET("/arguments/foo/bar/10");
            }
        },
        /**
         * A path variable, a header and a query value.
         */
        PATH_HEADER_QUERY("42 acme 3") {
            @Override
            MutableHttpRequest<?> request() {
                return HttpRequest.GET("/arguments/books/42?page=3").header("X-Tenant", "acme");
            }
        },
        /**
         * Arguments without binding annotations, bound by the unmatched binder chain.
         */
        UNANNOTATED_QUERY("xy3") {
            @Override
            MutableHttpRequest<?> request() {
                return HttpRequest.GET("/arguments/unannotated?a=x&b=y&c=3");
            }
        },
        /**
         * A {@code @RequestBean} with a path variable, a header and a query value.
         */
        REQUEST_BEAN("42 acme 3") {
            @Override
            MutableHttpRequest<?> request() {
                return HttpRequest.GET("/arguments/bean/42?page=3").header("X-Tenant", "acme");
            }
        };

        final String expected;

        Scenario(String expected) {
            this.expected = expected;
        }

        abstract MutableHttpRequest<?> request();
    }
}
