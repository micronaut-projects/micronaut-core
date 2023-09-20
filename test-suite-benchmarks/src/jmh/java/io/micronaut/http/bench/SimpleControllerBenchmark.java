/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.bench;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpStatus;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SimpleControllerBenchmark {
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private HttpClient client;
        private URI uri;
        private ApplicationContext context;
        private HttpRequest listPersons;

        @Setup
        public void setup() {
            context = Micronaut.run(BenchmarkState.class);
            uri = context.getBean(EmbeddedServer.class).getContextURI();
            client = HttpClient.newBuilder()
                .build();
            listPersons = HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .build();
        }

        @TearDown
        public void shutdown() {
            context.close();
        }

    }

    @Benchmark
    public void getSomeJson(Blackhole blackhole, BenchmarkState app) throws IOException, InterruptedException {
        var response = app.client.send(app.listPersons, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == HttpStatus.OK.getCode()) {
            blackhole.consume(
                response.body()
            );
        }
    }
}
