/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.binding;

import io.micronaut.aop.around.AroundCompileBenchmark;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class RequestArgumentSatisfierBenchmark {

    ApplicationContext applicationContext;
    RequestArgumentSatisfier requestArgumentSatisfier;
    Router router;

    @Setup
    public void setup() {
        applicationContext = ApplicationContext.run();
        requestArgumentSatisfier = applicationContext.getBean(RequestArgumentSatisfier.class);
        router = applicationContext.getBean(Router.class);
    }

    @Benchmark
    public void benchmarkFulfillArgumentRequirements() {
        final MutableHttpRequest<Object> request = HttpRequest.GET("/arguments/foo/bar/10");
        final UriRouteMatch<Object, Object> routeMatch = router.find(request.getMethod(), request.getUri().toString(), request).findFirst().orElse(null);
        final RouteMatch<?> transformed = requestArgumentSatisfier.fulfillArgumentRequirements(
                routeMatch,
                request,
                true
        );
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + AroundCompileBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }

}
