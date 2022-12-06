/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tck.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.reactivestreams.Publisher;

import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

interface AbstractTck {

    default void runTest(String specName, TckSpec spec) {
        try (
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", specName));
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())
        ) {
            spec.test(server, client);
        }
    }

    default void runTest(String specName, TwoServerTckSpec spec) {
        try (
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", specName));
            EmbeddedServer otherServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", specName, "redirect.server", true));
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())
        ) {
            spec.test(server, otherServer, client);
        }
    }

    default void runTest(String specName, String contextPath, TckSpec spec) {
        try (
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", specName));
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, new LoadBalancer() {
                @Override
                public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                    URL url = server.getURL();
                    return Publishers.just(ServiceInstance.of(url.getHost(), url));
                }

                @Override
                public Optional<String> getContextPath() {
                    return Optional.of(contextPath);
                }
            })
        ) {
            spec.test(server, client);
        }
    }

    default void runBlockingTest(String specName, BlockingTckSpec spec) {
        try (
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", specName));
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())
        ) {
            spec.test(server, client.toBlocking());
        }
    }

    default void runBlockingTest(String specName, TwoServerBlockingTckSpec spec) {
        try (
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", specName));
            EmbeddedServer otherServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", specName, "redirect.server", true));
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())
        ) {
            spec.test(server, otherServer, client.toBlocking());
        }
    }

    default void runBlockingTest(String specName, String contextPath, BlockingTckSpec spec) {
        try (
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", specName));
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, new LoadBalancer() {
                @Override
                public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                    URL url = server.getURL();
                    return Publishers.just(ServiceInstance.of(url.getHost(), url));
                }

                @Override
                public Optional<String> getContextPath() {
                    return Optional.of(contextPath);
                }
            })
        ) {
            spec.test(server, client.toBlocking());
        }
    }


    @FunctionalInterface
    interface TckSpec {
        void test(EmbeddedServer server, HttpClient client);
    }

    @FunctionalInterface
    interface TwoServerTckSpec {
        void test(EmbeddedServer server, EmbeddedServer otherServer, HttpClient client);
    }

    @FunctionalInterface
    interface BlockingTckSpec {
        void test(EmbeddedServer server, BlockingHttpClient client);
    }

    @FunctionalInterface
    interface TwoServerBlockingTckSpec {
        void test(EmbeddedServer server, EmbeddedServer otherServer, BlockingHttpClient client);
    }
}
