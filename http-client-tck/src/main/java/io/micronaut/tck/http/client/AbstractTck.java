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

public interface AbstractTck {

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
}
