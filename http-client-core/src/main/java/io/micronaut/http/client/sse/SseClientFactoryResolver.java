package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientFactory;

import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

@Internal
class SseClientFactoryResolver {

    private static SseClientFactory factory;

    @NonNull
    static SseClient createClient(URL url) {
        return getFactory().createSseClient(url);
    }

    @NonNull
    static SseClient createClient(URL url, HttpClientConfiguration configuration) {
        return getFactory().createSseClient(url, configuration);
    }

    private static SseClientFactory getFactory() {
        if (factory == null) {
            synchronized (SseClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static SseClientFactory resolveClientFactory() {
        final Iterator<SseClientFactory> i = ServiceLoader.load(SseClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No SseClientFactory present on classpath, cannot create sse client");
    }
}
